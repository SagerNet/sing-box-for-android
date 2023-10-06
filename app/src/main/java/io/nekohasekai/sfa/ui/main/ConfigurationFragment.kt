package io.nekohasekai.sfa.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.databinding.FragmentConfigurationBinding
import io.nekohasekai.sfa.databinding.ViewConfigutationItemBinding
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.shareProfile
import io.nekohasekai.sfa.ui.profile.EditProfileActivity
import io.nekohasekai.sfa.ui.profile.NewProfileActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class ConfigurationFragment : Fragment() {

    private var adapter: Adapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        val adapter = Adapter(lifecycleScope, binding)
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
            startActivity(Intent(requireContext(), NewProfileActivity::class.java))
        }
        ProfileManager.registerCallback(this::updateProfiles)
        return binding.root
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

    class Adapter(
        internal val scope: CoroutineScope,
        internal val parent: FragmentConfigurationBinding
    ) :
        RecyclerView.Adapter<Holder>() {

        internal var items: MutableList<Profile> = mutableListOf()

        internal fun reload() {
            scope.launch(Dispatchers.IO) {
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
            binding.root.setOnClickListener {
                val intent = Intent(binding.root.context, EditProfileActivity::class.java)
                intent.putExtra("profile_id", profile.id)
                it.context.startActivity(intent)
            }
            binding.moreButton.setOnClickListener { button ->
                val popup = PopupMenu(button.context, button)
                popup.setForceShowIcon(true)
                popup.menuInflater.inflate(R.menu.profile_menu, popup.menu)
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