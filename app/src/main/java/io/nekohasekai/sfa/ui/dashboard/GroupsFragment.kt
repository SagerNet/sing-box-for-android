package io.nekohasekai.sfa.ui.dashboard

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.OutboundGroupItem
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.databinding.FragmentDashboardGroupsBinding
import io.nekohasekai.sfa.databinding.ViewDashboardGroupBinding
import io.nekohasekai.sfa.databinding.ViewDashboardGroupItemBinding
import io.nekohasekai.sfa.ktx.colorForURLTestDelay
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ui.MainActivity
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class GroupsFragment : Fragment(), CommandClient.Handler {

    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var _binding: FragmentDashboardGroupsBinding? = null
    private val binding get() = _binding!!

    private var _adapter: Adapter? = null
    private val adapter get() = _adapter!!

    private val commandClient =
        CommandClient(lifecycleScope, CommandClient.ConnectionType.Groups, this)


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardGroupsBinding.inflate(inflater, container, false)
        onCreate()
        return binding.root
    }

    private fun onCreate() {
        val activity = activity ?: return
        _adapter = Adapter()
        binding.container.adapter = adapter
        binding.container.layoutManager = LinearLayoutManager(requireContext())
        activity.serviceStatus.observe(viewLifecycleOwner) {
            if (it == Status.Started) {
                commandClient.connect()
            }
        }
    }

    private var displayed = false
    private fun updateDisplayed(newValue: Boolean) {
        if (displayed != newValue) {
            displayed = newValue
            binding.statusText.isVisible = !displayed
            binding.container.isVisible = displayed
        }
    }

    override fun onConnected() {
        lifecycleScope.launch(Dispatchers.Main) {
            updateDisplayed(true)
        }
    }

    override fun onDisconnected() {
        lifecycleScope.launch(Dispatchers.Main) {
            updateDisplayed(false)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun updateGroups(groups: List<OutboundGroup>) {
        activity?.runOnUiThread {
            updateDisplayed(groups.isNotEmpty())
            adapter.groups = groups
            adapter.notifyDataSetChanged()
        }
    }

    private class Adapter : RecyclerView.Adapter<GroupView>() {

        lateinit var groups: List<OutboundGroup>
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupView {
            return GroupView(
                ViewDashboardGroupBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
            )
        }

        override fun getItemCount(): Int {
            if (!::groups.isInitialized) {
                return 0
            }
            return groups.size
        }

        override fun onBindViewHolder(holder: GroupView, position: Int) {
            holder.bind(groups[position])
        }
    }

    private class GroupView(val binding: ViewDashboardGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        lateinit var group: OutboundGroup
        lateinit var items: MutableList<OutboundGroupItem>
        lateinit var adapter: ItemAdapter
        fun bind(group: OutboundGroup) {
            this.group = group
            binding.groupName.text = group.tag
            binding.groupType.text = Libbox.proxyDisplayType(group.type)
            binding.urlTestButton.setOnClickListener {
                GlobalScope.launch {
                    runCatching {
                        Libbox.newStandaloneCommandClient().urlTest(group.tag)
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            binding.root.context.errorDialogBuilder(it).show()
                        }
                    }
                }
            }
            items = mutableListOf()
            val itemIterator = group.items
            while (itemIterator.hasNext()) {
                items.add(itemIterator.next())
            }
            adapter = ItemAdapter(this, group, items)
            binding.itemList.adapter = adapter
            (binding.itemList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            binding.itemList.layoutManager = GridLayoutManager(binding.root.context, 2)
            updateExpand()
        }

        private fun updateExpand(isExpand: Boolean? = null) {
            val newExpandStatus = isExpand ?: group.isExpand
            if (isExpand != null) {
                GlobalScope.launch {
                    runCatching {
                        Libbox.newStandaloneCommandClient().setGroupExpand(group.tag, isExpand)
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            binding.root.context.errorDialogBuilder(it).show()
                        }
                    }
                }
            }
            binding.itemList.isVisible = newExpandStatus
            binding.itemText.isVisible = !newExpandStatus
            if (!newExpandStatus) {
                val builder = SpannableStringBuilder()
                items.forEach {
                    if (it.tag == group.selected) {
                        builder.append("▣")
                    } else {
                        builder.append("■")
                    }
                    builder.setSpan(
                        ForegroundColorSpan(
                            colorForURLTestDelay(
                                binding.root.context,
                                it.urlTestDelay
                            )
                        ),
                        builder.length - 1,
                        builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.append(" ")
                }
                binding.itemText.text = builder
            }
            if (newExpandStatus) {
                binding.expandButton.setImageResource(R.drawable.ic_expand_less_24)
            } else {
                binding.expandButton.setImageResource(R.drawable.ic_expand_more_24)
            }
            binding.expandButton.setOnClickListener {
                updateExpand(!binding.itemList.isVisible)
            }
        }

        fun updateSelected(group: OutboundGroup, item: OutboundGroupItem) {
            val oldSelected = items.indexOfFirst { it.tag == group.selected }
            group.selected = item.tag
            if (oldSelected != -1) {
                adapter.notifyItemChanged(oldSelected)
            }
        }
    }

    private class ItemAdapter(
        val groupView: GroupView,
        val group: OutboundGroup,
        val items: List<OutboundGroupItem>
    ) :
        RecyclerView.Adapter<ItemGroupView>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemGroupView {
            return ItemGroupView(
                ViewDashboardGroupItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onBindViewHolder(holder: ItemGroupView, position: Int) {
            holder.bind(groupView, group, items[position])
        }
    }

    private class ItemGroupView(val binding: ViewDashboardGroupItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(groupView: GroupView, group: OutboundGroup, item: OutboundGroupItem) {
            if (group.selectable) {
                binding.itemCard.setOnClickListener {
                    binding.selectedView.isVisible = true
                    groupView.updateSelected(group, item)
                    GlobalScope.launch {
                        runCatching {
                            Libbox.newStandaloneCommandClient().selectOutbound(group.tag, item.tag)
                        }.onFailure {
                            withContext(Dispatchers.Main) {
                                binding.root.context.errorDialogBuilder(it).show()
                            }
                        }
                    }
                }
            }
            binding.selectedView.isInvisible = group.selected != item.tag
            binding.itemName.text = item.tag
            binding.itemType.text = Libbox.proxyDisplayType(item.type)
            binding.itemStatus.isVisible = item.urlTestTime > 0
            if (item.urlTestTime > 0) {
                binding.itemStatus.text = "${item.urlTestDelay}ms"
                binding.itemStatus.setTextColor(
                    colorForURLTestDelay(
                        binding.root.context,
                        item.urlTestDelay
                    )
                )
            }
        }
    }

}

