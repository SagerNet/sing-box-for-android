package io.nekohasekai.sfa.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.databinding.FragmentDashboardBinding
import io.nekohasekai.sfa.ui.MainActivity
import io.nekohasekai.sfa.ui.dashboard.GroupsFragment
import io.nekohasekai.sfa.ui.dashboard.OverviewFragment

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

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

    private fun onCreate() {
        val activity = activity ?: return
        binding.dashboardPager.adapter = Adapter(this)
        TabLayoutMediator(binding.dashboardTabLayout, binding.dashboardPager) { tab, position ->
            tab.setText(Page.values()[position].titleRes)
        }.attach()
        activity.serviceStatus.observe(viewLifecycleOwner) {
            when (it) {
                Status.Stopped -> {
                    binding.fab.setImageResource(R.drawable.ic_play_arrow_24)
                    binding.fab.show()
                    binding.dashboardTabLayout.isVisible = false
                    binding.dashboardPager.isUserInputEnabled = false
                }

                Status.Starting -> {
                    binding.fab.hide()
                }

                Status.Started -> {
                    binding.fab.setImageResource(R.drawable.ic_stop_24)
                    binding.fab.show()
                    binding.dashboardTabLayout.isVisible = true
                    binding.dashboardPager.isUserInputEnabled = true
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

    enum class Page(@StringRes val titleRes: Int, val fragmentClass: Class<out Fragment>) {
        Overview(R.string.title_overview, OverviewFragment::class.java),
        Groups(R.string.title_groups, GroupsFragment::class.java);
    }

    class Adapter(parent: Fragment) : FragmentStateAdapter(parent) {
        override fun getItemCount(): Int {
            return Page.values().size
        }

        override fun createFragment(position: Int): Fragment {
            return Page.values()[position].fragmentClass.newInstance()
        }
    }

}