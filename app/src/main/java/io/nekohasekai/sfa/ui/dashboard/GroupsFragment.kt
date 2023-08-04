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
import go.Seq
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.OutboundGroupItem
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.databinding.FragmentDashboardGroupsBinding
import io.nekohasekai.sfa.databinding.ViewDashboardGroupBinding
import io.nekohasekai.sfa.databinding.ViewDashboardGroupItemBinding
import io.nekohasekai.sfa.ktx.colorForURLTestDelay
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class GroupsFragment : Fragment(), CommandClientHandler {

    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var _binding: FragmentDashboardGroupsBinding? = null
    private val binding get() = _binding!!
    private var commandClient: CommandClient? = null

    private var _adapter: Adapter? = null
    private val adapter get() = _adapter!!

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
                reconnect()
            }
        }
    }

    private fun reconnect() {
        disconnect()
        val options = CommandClientOptions()
        options.command = Libbox.CommandGroup
        options.statusInterval = 2 * 1000 * 1000 * 1000
        val commandClient = CommandClient(requireContext().filesDir.absolutePath, this, options)
        this.commandClient = commandClient
        lifecycleScope.launch(Dispatchers.IO) {
            for (i in 1..3) {
                delay(100)
                try {
                    commandClient.connect()
                    break
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun disconnect() {
        commandClient?.apply {
            runCatching {
                disconnect()
            }
            Seq.destroyRef(refnum)
        }
        commandClient = null
    }

    override fun connected() {
        val binding = _binding ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            binding.statusText.isVisible = false
            binding.container.isVisible = true
        }
    }

    override fun disconnected(message: String?) {
        val binding = _binding ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            binding.statusText.isVisible = true
            binding.container.isVisible = false
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun writeGroups(message: OutboundGroupIterator) {
        val groups = mutableListOf<OutboundGroup>()
        while (message.hasNext()) {
            groups.add(message.next())
        }
        activity?.runOnUiThread {
            adapter.groups = groups
            adapter.notifyDataSetChanged()
        }
    }

    override fun writeLog(message: String?) {
    }

    override fun writeStatus(message: StatusMessage?) {
    }

    private class Adapter : RecyclerView.Adapter<GroupView>() {

        lateinit var groups: List<OutboundGroup>
        private val expandStatus: MutableMap<String, Boolean> = mutableMapOf()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupView {
            return GroupView(
                ViewDashboardGroupBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                expandStatus
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

    private class GroupView(
        val binding: ViewDashboardGroupBinding,
        val expandStatus: MutableMap<String, Boolean>
    ) :
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
            val newExpandStatus: Boolean
            if (isExpand == null) {
                newExpandStatus = expandStatus[group.tag] ?: group.selectable
            } else {
                expandStatus[group.tag] = isExpand
                newExpandStatus = isExpand
            }
            binding.itemList.isVisible = newExpandStatus
            binding.itemText.isVisible = !newExpandStatus
            if (!newExpandStatus) {
                val builder = SpannableStringBuilder()
                items.forEach {
                    builder.append("■")
                    builder.setSpan(
                        ForegroundColorSpan(colorForURLTestDelay(it.urlTestDelay)),
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
            binding.selectedView.isInvisible = group.selected != item.tag
            binding.itemName.text = item.tag
            binding.itemType.text = Libbox.proxyDisplayType(item.type)
            binding.itemStatus.isVisible = item.urlTestTime > 0
            if (item.urlTestTime > 0) {
                binding.itemStatus.text = "${item.urlTestDelay}ms"
                binding.itemStatus.setTextColor(colorForURLTestDelay(item.urlTestDelay))
            }
        }
    }

}

