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
    private var binding: FragmentDashboardBinding? = null
    private var mediator: TabLayoutMediator? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentDashboardBinding.inflate(inflater, container, false)
        this.binding = binding
        onCreate()
        return binding.root
    }

    private fun onCreate() {
        val activity = activity ?: return
        val binding = binding ?: return
        binding.dashboardPager.adapter = Adapter(this)
        binding.dashboardPager.offscreenPageLimit = Page.values().size
        activity.serviceStatus.observe(viewLifecycleOwner) {
            when (it) {
                Status.Stopped -> {
                    disablePager()
                    binding.fab.setImageResource(R.drawable.ic_play_arrow_24)
                    binding.fab.show()
                }

                Status.Starting -> {
                    binding.fab.hide()
                }

                Status.Started -> {
                    enablePager()
                    binding.fab.setImageResource(R.drawable.ic_stop_24)
                    binding.fab.show()
                    binding.fab.isEnabled = true
                }

                Status.Stopping -> {
                    disablePager()
                    binding.fab.hide()
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

    override fun onStart() {
        super.onStart()
        val activityBinding = activity?.binding ?: return
        val binding = binding ?: return
        if (mediator != null) return
        mediator = TabLayoutMediator(
            activityBinding.dashboardTabLayout,
            binding.dashboardPager
        ) { tab, position ->
            tab.setText(Page.values()[position].titleRes)
        }.apply { attach() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediator?.detach()
        mediator = null
        binding = null
    }

    private fun enablePager() {
        val activity = activity ?: return
        val binding = binding ?: return
        activity.binding.dashboardTabLayout.isVisible = true
        binding.dashboardPager.isUserInputEnabled = true
    }

    private fun disablePager() {
        val activity = activity ?: return
        val binding = binding ?: return
        activity.binding.dashboardTabLayout.isVisible = false
        binding.dashboardPager.isUserInputEnabled = false
        binding.dashboardPager.setCurrentItem(0, false)
    }

    enum class Page(@StringRes val titleRes: Int, val fragmentClass: Class<out Fragment>) {
        Overview(R.string.title_overview, OverviewFragment::class.java),
        Groups(R.string.title_groups, GroupsFragment::class.java);
    }

    class Adapter(parent: Fragment) : FragmentStateAdapter(parent) {
        override fun getItemCount(): Int {
            return Page.entries.size
        }

        override fun createFragment(position: Int): Fragment {
            return Page.entries[position].fragmentClass.getConstructor().newInstance()
        }
    }

}