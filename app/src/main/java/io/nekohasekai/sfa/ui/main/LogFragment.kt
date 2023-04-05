package io.nekohasekai.sfa.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.databinding.FragmentLogBinding
import io.nekohasekai.sfa.databinding.ViewLogTextItemBinding
import io.nekohasekai.sfa.ui.MainActivity
import io.nekohasekai.sfa.utils.ColorUtils
import java.util.LinkedList

class LogFragment : Fragment() {
    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!
    private var logAdapter: LogAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        onCreate()
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (_binding != null) onCreate()
    }

    private fun onCreate() {
        val activity = activity ?: return
        activity.logCallback = ::updateViews
        binding.logView.layoutManager = LinearLayoutManager(requireContext())
        binding.logView.adapter = LogAdapter(activity.logList).also { logAdapter = it }
        updateViews(true)
        activity.serviceStatus.observe(viewLifecycleOwner) {
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

    private fun updateViews(reset: Boolean) {
        val activity = activity ?: return
        val logAdapter = logAdapter ?: return
        if (activity.logList.isEmpty()) {
            binding.logView.isVisible = false
            binding.statusText.isVisible = true
        } else if (!binding.logView.isVisible) {
            binding.logView.isVisible = true
            binding.statusText.isVisible = false
        }
        if (reset) {
            logAdapter.notifyDataSetChanged()
            binding.logView.scrollToPosition(activity.logList.size - 1)
        } else {
            binding.logView.scrollToPosition(logAdapter.notifyItemInserted())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        activity?.logCallback = null
        logAdapter = null
    }


    class LogAdapter(private val logList: LinkedList<String>) :
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

        fun notifyItemInserted(): Int {
            if (logList.size > 300) {
                logList.removeFirst()
                notifyItemRemoved(0)
            }

            val position = logList.size - 1
            notifyItemInserted(position)
            return position
        }

    }

    class LogViewHolder(private val binding: ViewLogTextItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: String) {
            binding.text.text = ColorUtils.ansiEscapeToSpannable(binding.root.context, message)
        }
    }

}