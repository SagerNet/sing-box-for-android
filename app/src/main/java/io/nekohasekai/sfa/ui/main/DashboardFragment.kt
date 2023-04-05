package io.nekohasekai.sfa.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.databinding.FragmentDashboardBinding
import io.nekohasekai.sfa.ui.MainActivity

class DashboardFragment : Fragment() {

    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        onCreate()
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (_binding != null) onCreate()
    }

    private fun onCreate() {
        val activity = activity ?: return

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
                    binding.fab.setImageResource(R.drawable.ic_stop_24)
                    binding.fab.show()
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
                    activity.startService()
                }

                Status.Started -> {
                    BoxService.stop()
                }

                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}