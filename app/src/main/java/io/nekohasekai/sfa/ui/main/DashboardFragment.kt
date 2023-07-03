package io.nekohasekai.sfa.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import go.Seq
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.Profiles
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.FragmentDashboardBinding
import io.nekohasekai.sfa.databinding.ViewProfileItemBinding
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment(), CommandClientHandler {

    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private var commandClient: CommandClient? = null

    private var _adapter: Adapter? = null
    private val adapter get() = _adapter!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        onCreate()
        return binding.root
    }

    private fun onCreate() {
        val activity = activity ?: return

        binding.profileList.adapter = Adapter(lifecycleScope, binding).apply {
            _adapter = this
            reload()
        }
        binding.profileList.layoutManager = LinearLayoutManager(requireContext())
        val divider = MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL)
        divider.isLastItemDecorated = false
        binding.profileList.addItemDecoration(divider)

        activity.serviceStatus.observe(viewLifecycleOwner) {
            binding.statusCard.isVisible = it == Status.Starting || it == Status.Started
            when (it) {
                Status.Stopped -> {
                    binding.fab.setImageResource(R.drawable.ic_play_arrow_24)
                    binding.fab.show()
                }

                Status.Starting -> {
                    binding.fab.hide()
                }

                Status.Started -> {
                    binding.fab.setImageResource(R.drawable.ic_stop_24)
                    binding.fab.show()
                    reconnect()
                }

                Status.Stopping -> {
                    binding.fab.hide()
                }

                else -> {}
            }
        }
        binding.fab.setOnClickListener {
            when (activity.serviceStatus.value) {
                Status.Stopped -> {
                    activity.startService()
                }

                Status.Started -> {
                    BoxService.stop()
                }

                else -> {}
            }
        }
    }

    private fun reconnect() {
        disconnect()
        val options = CommandClientOptions()
        options.command = Libbox.CommandStatus
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

    override fun onDestroyView() {
        super.onDestroyView()
        _adapter = null
        _binding = null
        disconnect()
    }

    override fun connected() {
        val binding = _binding ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            binding.memoryText.text = getString(R.string.loading)
            binding.goroutinesText.text = getString(R.string.loading)
        }
    }

    override fun disconnected(message: String?) {
        val binding = _binding ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            binding.memoryText.text = getString(R.string.loading)
            binding.goroutinesText.text = getString(R.string.loading)
        }
    }

    override fun writeLog(message: String) {
    }

    override fun writeStatus(message: StatusMessage) {
        val binding = _binding ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            binding.memoryText.text = Libbox.formatBytes(message.memory)
            binding.goroutinesText.text = message.goroutines.toString()
        }
    }

    override fun writeGroups(message: OutboundGroupIterator?) {
    }

    class Adapter(
        internal val scope: CoroutineScope,
        private val parent: FragmentDashboardBinding
    ) :
        RecyclerView.Adapter<Holder>() {

        internal var items: MutableList<Profile> = mutableListOf()
        internal var selectedProfileID = -1L
        internal var lastSelectedIndex: Int? = null
        internal fun reload() {
            scope.launch(Dispatchers.IO) {
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
                    parent.statusText.isVisible = items.isEmpty()
                    parent.container.isVisible = items.isNotEmpty()
                    notifyDataSetChanged()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                this,
                ViewProfileItemBinding.inflate(
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

    class Holder(
        private val adapter: Adapter,
        private val binding: ViewProfileItemBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {

        internal fun bind(profile: Profile) {
            binding.profileName.text = profile.name
            binding.profileSelected.setOnCheckedChangeListener(null)
            binding.profileSelected.isChecked = profile.id == adapter.selectedProfileID
            binding.profileSelected.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    adapter.selectedProfileID = profile.id
                    adapter.lastSelectedIndex?.let { index ->
                        adapter.notifyItemChanged(index)
                    }
                    adapter.lastSelectedIndex = adapterPosition
                    adapter.scope.launch(Dispatchers.IO) {
                        switchProfile(profile)
                    }
                }
            }
            binding.root.setOnClickListener {
                binding.profileSelected.toggle()
            }
        }

        private suspend fun switchProfile(profile: Profile) {
            Settings.selectedProfile = profile.id
            val mainActivity = (binding.root.context as? MainActivity) ?: return
            val started = mainActivity.serviceStatus.value == Status.Started
            if (!started) {
                return
            }
            val restart = Settings.rebuildServiceMode()
            if (restart) {
                mainActivity.reconnect()
                BoxService.stop()
                delay(200)
                mainActivity.startService()
                return
            }
            runCatching {
                Libbox.newStandaloneCommandClient(mainActivity.filesDir.absolutePath).serviceReload()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    mainActivity.errorDialogBuilder(it).show()
                }
            }
        }
    }

}