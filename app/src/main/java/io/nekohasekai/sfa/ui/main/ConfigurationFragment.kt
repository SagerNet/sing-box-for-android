package io.nekohasekai.sfa.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.Profiles
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.FragmentConfigurationBinding
import io.nekohasekai.sfa.databinding.ViewConfigutationItemBinding
import io.nekohasekai.sfa.ui.profile.EditProfileActivity
import io.nekohasekai.sfa.ui.profile.NewProfileActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigurationFragment : Fragment() {

    private var _adapter: Adapter? = null
    private var adapter: Adapter
        get() = _adapter as Adapter
        set(value) {
            _adapter = value
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        adapter = Adapter(binding)
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
            }).attachToRecyclerView(it)
        }
        adapter.reload()
        binding.fab.setOnClickListener {
            startActivity(Intent(requireContext(), NewProfileActivity::class.java))
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        _adapter?.reload()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _adapter = null
    }

    class Adapter(private val parent: FragmentConfigurationBinding) :
        RecyclerView.Adapter<Holder>() {

        internal var items: MutableList<Profile> = mutableListOf()
        internal var selectedProfileID = -1L
        internal var lastSelectedIndex: Int? = null

        internal fun reload() {
            GlobalScope.launch(Dispatchers.IO) {
                items = Profiles.list().toMutableList()
                if (items.isNotEmpty()) {
                    selectedProfileID = Settings.selectedProfile
                    for ((index, profile) in items.withIndex()) {
                        if (profile.id == selectedProfileID) {
                            lastSelectedIndex = index
                            break
                        }
                    }
                    if (lastSelectedIndex == null) {
                        lastSelectedIndex = 0
                        selectedProfileID = items[0].id
                        Settings.selectedProfile = selectedProfileID
                    }
                }
                withContext(Dispatchers.Main) {
                    if (items.isEmpty()) {
                        parent.statusText.isVisible = true
                        parent.profileList.isVisible = false
                    } else if (parent.statusText.isVisible) {
                        parent.statusText.isVisible = false
                        parent.profileList.isVisible = true
                    }
                    notifyDataSetChanged()
                }
            }
        }

        internal fun move(from: Int, to: Int): Boolean {
            val first = items.getOrNull(from) ?: return false
            var previousOrder = first.userOrder
            val (step, range) = if (from < to) Pair(1, from until to) else Pair(
                -1, to + 1 downTo from
            )
            val updated = mutableListOf<Profile>()
            for (i in range) {
                val next = items.getOrNull(i + step) ?: return false
                val order = next.userOrder
                next.userOrder = previousOrder
                previousOrder = order
                updated.add(next)
            }
            first.userOrder = previousOrder
            updated.add(first)
            notifyItemMoved(from, to)
            GlobalScope.launch(Dispatchers.IO) {
                Profiles.update(updated)
            }
            return true
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
            binding.selectedView.isInvisible = adapter.selectedProfileID != profile.id
            binding.profileName.text = profile.name
            binding.root.setOnClickListener {
                binding.selectedView.isVisible = true
                adapter.selectedProfileID = profile.id
                adapter.lastSelectedIndex?.let { index ->
                    adapter.notifyItemChanged(index)
                }
                adapter.lastSelectedIndex = adapterPosition
                GlobalScope.launch(Dispatchers.IO) {
                    Settings.selectedProfile = profile.id
                }
            }
            binding.editButton.setOnClickListener {
                val intent = Intent(binding.root.context, EditProfileActivity::class.java)
                intent.putExtra("profile_id", profile.id)
                it.context.startActivity(intent)
            }
            binding.moreButton.setOnClickListener { it ->
                val popup = PopupMenu(it.context, it)
                popup.setForceShowIcon(true)
                popup.menuInflater.inflate(R.menu.profile_menu, popup.menu)
                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.action_delete -> {
                            adapter.items.remove(profile)
                            adapter.notifyItemRemoved(adapterPosition)
                            GlobalScope.launch(Dispatchers.IO) {
                                runCatching {
                                    Profiles.delete(profile)
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