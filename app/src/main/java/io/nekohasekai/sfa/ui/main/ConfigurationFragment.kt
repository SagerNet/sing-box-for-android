package io.nekohasekai.sfa.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.databinding.FragmentConfigurationBinding
import io.nekohasekai.sfa.databinding.SheetAddProfileBinding
import io.nekohasekai.sfa.databinding.ViewConfigutationItemBinding
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.shareProfile
import io.nekohasekai.sfa.ktx.shareProfileURL
import io.nekohasekai.sfa.ui.MainActivity
import io.nekohasekai.sfa.ui.profile.EditProfileActivity
import io.nekohasekai.sfa.ui.profile.NewProfileActivity
import io.nekohasekai.sfa.ui.profile.QRScanActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Collections

class ConfigurationFragment : Fragment() {

    private var adapter: Adapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        val adapter = Adapter(binding)
        this.adapter = adapter
        binding.profileList.also {
            it.layoutManager = LinearLayoutManager(requireContext())
            it.adapter = adapter
            ItemTouchHelper(object :
                ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return adapter.move(viewHolder.adapterPosition, target.adapterPosition)
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                }

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?,
                    actionState: Int
                ) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                        adapter.updateUserOrder()
                    }
                }
            }).attachToRecyclerView(it)
        }
        adapter.reload()
        binding.fab.setOnClickListener {
            AddProfileDialog().show(childFragmentManager, "add_profile")
        }
        ProfileManager.registerCallback(this::updateProfiles)
        return binding.root
    }

    class AddProfileDialog : BottomSheetDialogFragment(R.layout.sheet_add_profile) {

        private val importFromFile =
            registerForActivityResult(ActivityResultContracts.GetContent(), ::onImportResult)

        private val scanQrCode =
            registerForActivityResult(QRScanActivity.Contract(), ::onScanResult)

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val binding = SheetAddProfileBinding.bind(view)
            binding.importFromFile.setOnClickListener {
                importFromFile.launch("*/*")
            }
            binding.scanQrCode.setOnClickListener {
                scanQrCode.launch(null)
            }
            binding.createManually.setOnClickListener {
                dismiss()
                startActivity(Intent(requireContext(), NewProfileActivity::class.java))
            }
        }

        private fun onImportResult(result: Uri?) {
            dismiss()
            (activity as? MainActivity ?: return).onNewIntent(Intent(Intent.ACTION_VIEW, result))
        }

        private fun onScanResult(result: Intent?) {
            dismiss()
            (activity as? MainActivity ?: return).onNewIntent(result ?: return)
        }
    }

    override fun onResume() {
        super.onResume()
        adapter?.reload()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ProfileManager.unregisterCallback(this::updateProfiles)
        adapter = null
    }

    private fun updateProfiles() {
        adapter?.reload()
    }

    inner class Adapter(
        private val parent: FragmentConfigurationBinding
    ) :
        RecyclerView.Adapter<Holder>() {

        internal var items: MutableList<Profile> = mutableListOf()
        internal val scope = lifecycleScope
        internal val fragmentActivity = requireActivity()

        @SuppressLint("NotifyDataSetChanged")
        internal fun reload() {
            lifecycleScope.launch(Dispatchers.IO) {
                val newItems = ProfileManager.list().toMutableList()
                withContext(Dispatchers.Main) {
                    items = newItems
                    notifyDataSetChanged()
                    if (items.isEmpty()) {
                        parent.statusText.isVisible = true
                        parent.profileList.isVisible = false
                    } else if (parent.statusText.isVisible) {
                        parent.statusText.isVisible = false
                        parent.profileList.isVisible = true
                    }
                }
            }
        }

        internal fun move(from: Int, to: Int): Boolean {
            if (from < to) {
                for (i in from until to) {
                    Collections.swap(items, i, i + 1)
                }
            } else {
                for (i in from downTo to + 1) {
                    Collections.swap(items, i, i - 1)
                }
            }
            notifyItemMoved(from, to)
            return true
        }

        @OptIn(DelicateCoroutinesApi::class)
        internal fun updateUserOrder() {
            items.forEachIndexed { index, profile ->
                profile.userOrder = index.toLong()
            }
            GlobalScope.launch(Dispatchers.IO) {
                ProfileManager.update(items)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                this,
                ViewConfigutationItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int {
            return items.size
        }

    }

    class Holder(private val adapter: Adapter, private val binding: ViewConfigutationItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        internal fun bind(profile: Profile) {
            binding.profileName.text = profile.name
            if (profile.typed.type == TypedProfile.Type.Remote) {
                binding.profileLastUpdated.isVisible = true
                binding.profileLastUpdated.text = binding.root.context.getString(
                    R.string.profile_item_last_updated,
                    DateFormat.getDateTimeInstance().format(profile.typed.lastUpdated)
                )
            } else {
                binding.profileLastUpdated.isVisible = false
            }
            binding.root.setOnClickListener {
                val intent = Intent(binding.root.context, EditProfileActivity::class.java)
                intent.putExtra("profile_id", profile.id)
                it.context.startActivity(intent)
            }
            binding.moreButton.setOnClickListener { button ->
                val popup = PopupMenu(button.context, button)
                popup.setForceShowIcon(true)
                popup.menuInflater.inflate(R.menu.profile_menu, popup.menu)
                if (profile.typed.type != TypedProfile.Type.Remote) {
                    popup.menu.removeItem(R.id.action_share_url)
                }
                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.action_share -> {
                            adapter.scope.launch(Dispatchers.IO) {
                                try {
                                    button.context.shareProfile(profile)
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        button.context.errorDialogBuilder(e).show()
                                    }
                                }
                            }
                            true
                        }

                        R.id.action_share_url -> {
                            adapter.scope.launch(Dispatchers.IO) {
                                try {
                                    adapter.fragmentActivity.shareProfileURL(profile)
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        button.context.errorDialogBuilder(e).show()
                                    }
                                }
                            }
                            true
                        }

                        R.id.action_delete -> {
                            adapter.items.remove(profile)
                            adapter.notifyItemRemoved(adapterPosition)
                            adapter.scope.launch(Dispatchers.IO) {
                                runCatching {
                                    ProfileManager.delete(profile)
                                }
                            }
                            true
                        }

                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

}