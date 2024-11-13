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
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.databinding.FragmentLogBinding
import io.nekohasekai.sfa.databinding.ViewLogTextItemBinding
import io.nekohasekai.sfa.ui.MainActivity
import io.nekohasekai.sfa.utils.ColorUtils
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.LinkedList

class LogFragment : Fragment(), CommandClient.Handler {
    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var binding: FragmentLogBinding? = null
    private var adapter: Adapter? = null
    private val commandClient =
        CommandClient(lifecycleScope, CommandClient.ConnectionType.Log, this)
    private val logList = LinkedList<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentLogBinding.inflate(inflater, container, false)
        this.binding = binding
        onCreate()
        return binding.root
    }

    private fun onCreate() {
        val activity = activity ?: return
        val binding = binding ?: return
        binding.logView.layoutManager = LinearLayoutManager(requireContext())
        binding.logView.adapter = Adapter(logList).also { adapter = it }
        updateViews()
        activity.serviceStatus.observe(viewLifecycleOwner) {
            when (it) {
                Status.Stopped -> {
                    binding.fab.setImageResource(R.drawable.ic_play_arrow_24)
                    binding.fab.show()
                    binding.statusText.setText(R.string.status_default)
                }

                Status.Starting -> {
                    binding.fab.hide()
                    binding.statusText.setText(R.string.status_starting)
                }

                Status.Started -> {
                    commandClient.connect()
                    binding.fab.setImageResource(R.drawable.ic_stop_24)
                    binding.fab.show()
                    binding.fab.isEnabled = true
                    binding.statusText.setText(R.string.status_started)
                }

                Status.Stopping -> {
                    binding.fab.hide()
                    binding.statusText.setText(R.string.status_stopping)
                }

                else -> {}
            }
        }
        binding.fab.setOnClickListener {
            when (activity.serviceStatus.value) {
                Status.Stopped -> {
                    it.isEnabled = false
                    activity.startService()
                }

                Status.Started -> {
                    BoxService.stop()
                }

                else -> {}
            }
        }
    }

    private fun updateViews(removeLen: Int = 0, insertLen: Int = 0) {
        val activity = activity ?: return
        val logAdapter = adapter ?: return
        val binding = binding ?: return
        if (logList.isEmpty()) {
            binding.logView.isVisible = false
            binding.statusText.isVisible = true
        } else if (!binding.logView.isVisible) {
            binding.logView.isVisible = true
            binding.statusText.isVisible = false
        }
        if (insertLen == 0) {
            logAdapter.notifyDataSetChanged()
            if (logList.size > 0) {
                binding.logView.scrollToPosition(logList.size - 1)
            }
        } else {
            if (logList.size == 300) {
                logAdapter.notifyItemRangeRemoved(0, removeLen)
            }
            logAdapter.notifyItemRangeInserted(logList.size - insertLen, insertLen)
            binding.logView.scrollToPosition(logList.size - 1)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        commandClient.disconnect()
        binding = null
        adapter = null
    }

    override fun onConnected() {
        lifecycleScope.launch(Dispatchers.Main) {
            logList.clear()
            updateViews()
        }
    }

    override fun clearLogs() {
        lifecycleScope.launch(Dispatchers.Main) {
            logList.clear()
            updateViews()
        }
    }

    override fun appendLogs(messageList: List<String>) {
        lifecycleScope.launch(Dispatchers.Main) {
            val messageLen = messageList.size
            val removeLen = logList.size + messageLen - 300
            logList.addAll(messageList)
            if (removeLen > 0) {
                repeat(removeLen) {
                    logList.removeFirst()
                }
            }
            updateViews(removeLen, messageLen)
        }
    }


    class Adapter(private val logList: LinkedList<String>) :
        RecyclerView.Adapter<LogViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            return LogViewHolder(
                ViewLogTextItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(logList.getOrElse(position) { "" })
        }

        override fun getItemCount(): Int {
            return logList.size
        }

    }

    class LogViewHolder(private val binding: ViewLogTextItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: String) {
            binding.text.text = ColorUtils.ansiEscapeToSpannable(binding.root.context, message)
        }
    }

}