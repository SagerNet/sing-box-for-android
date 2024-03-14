package io.nekohasekai.sfa.ui.profileoverride

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.ActivityPerAppProxy0Binding
import io.nekohasekai.sfa.databinding.DialogProgressbarBinding
import io.nekohasekai.sfa.databinding.ViewAppListItemBinding
import io.nekohasekai.sfa.ktx.clipboardText
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import java.io.File
import java.util.zip.ZipFile

class PerAppProxyActivity0 : AbstractActivity<ActivityPerAppProxy0Binding>() {
    enum class SortMode {
        NAME, PACKAGE_NAME, UID, INSTALL_TIME, UPDATE_TIME,
    }

    private var proxyMode = Settings.PER_APP_PROXY_INCLUDE
    private var sortMode = SortMode.NAME
    private var sortReverse = false
    private var hideSystemApps = false
    private var hideOfflineApps = true
    private var hideDisabledApps = true

    inner class PackageCache(
        private val packageInfo: PackageInfo,
    ) {

        val packageName: String get() = packageInfo.packageName

        val uid get() = packageInfo.applicationInfo.uid

        val installTime get() = packageInfo.firstInstallTime
        val updateTime get() = packageInfo.lastUpdateTime
        val isSystem get() = packageInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 1
        val isOffline get() = packageInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) != true
        val isDisabled get() = packageInfo.applicationInfo.flags and ApplicationInfo.FLAG_INSTALLED == 0

        val applicationIcon by lazy {
            packageInfo.applicationInfo.loadIcon(packageManager)
        }

        val applicationLabel by lazy {
            packageInfo.applicationInfo.loadLabel(packageManager).toString()
        }
    }

    private lateinit var adapter: ApplicationAdapter
    private var packages = listOf<PackageCache>()
    private var displayPackages = listOf<PackageCache>()
    private var currentPackages = listOf<PackageCache>()
    private var selectedUIDs = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_per_app_proxy)

        lifecycleScope.launch {
            proxyMode = if (Settings.perAppProxyMode == Settings.PER_APP_PROXY_EXCLUDE) {
                Settings.PER_APP_PROXY_EXCLUDE
            } else {
                Settings.PER_APP_PROXY_INCLUDE
            }
            withContext(Dispatchers.Main) {
                if (proxyMode != Settings.PER_APP_PROXY_EXCLUDE) {
                    binding.perAppProxyMode.setText(R.string.per_app_proxy_mode_include_description)
                } else {
                    binding.perAppProxyMode.setText(R.string.per_app_proxy_mode_exclude_description)
                }
            }
            reloadApplicationList()
            filterApplicationList()
            withContext(Dispatchers.Main) {
                adapter = ApplicationAdapter(displayPackages)
                binding.appList.adapter = adapter
                delay(500L)
                binding.progress.isVisible = false
            }
        }
    }

    private fun reloadApplicationList() {
        val packageManagerFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager.GET_PERMISSIONS or PackageManager.MATCH_UNINSTALLED_PACKAGES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_PERMISSIONS or PackageManager.GET_UNINSTALLED_PACKAGES
        }
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(
                    packageManagerFlags.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION") packageManager.getInstalledPackages(packageManagerFlags)
        }
        val packages = mutableListOf<PackageCache>()
        for (packageInfo in installedPackages) {
            if (packageInfo.packageName == packageName) continue
            packages.add(PackageCache(packageInfo))
        }
        val selectedPackageNames = Settings.perAppProxyList.toMutableSet()
        val selectedUIDs = mutableSetOf<Int>()
        for (packageCache in packages) {
            if (selectedPackageNames.contains(packageCache.packageName)) {
                selectedUIDs.add(packageCache.uid)
            }
        }
        this.packages = packages
        this.selectedUIDs = selectedUIDs
    }

    private fun filterApplicationList(selectedUIDs: Set<Int> = this.selectedUIDs) {
        val displayPackages = mutableListOf<PackageCache>()
        for (packageCache in packages) {
            if (hideSystemApps && packageCache.isSystem) continue
            if (hideOfflineApps && packageCache.isOffline) continue
            if (hideDisabledApps && packageCache.isDisabled) continue
            displayPackages.add(packageCache)
        }
        displayPackages.sortWith(compareBy<PackageCache> {
            !selectedUIDs.contains(it.uid)
        }.let {
            if (!sortReverse) it.thenBy {
                when (sortMode) {
                    SortMode.NAME -> it.applicationLabel
                    SortMode.PACKAGE_NAME -> it.packageName
                    SortMode.UID -> it.uid
                    SortMode.INSTALL_TIME -> it.installTime
                    SortMode.UPDATE_TIME -> it.updateTime
                }
            } else it.thenByDescending {
                when (sortMode) {
                    SortMode.NAME -> it.applicationLabel
                    SortMode.PACKAGE_NAME -> it.packageName
                    SortMode.UID -> it.uid
                    SortMode.INSTALL_TIME -> it.installTime
                    SortMode.UPDATE_TIME -> it.updateTime
                }
            }
        })

        this.displayPackages = displayPackages
        this.currentPackages = displayPackages
    }

    private fun updateApplicationSelection(packageCache: PackageCache, selected: Boolean) {
        val performed = if (selected) {
            selectedUIDs.add(packageCache.uid)
        } else {
            selectedUIDs.remove(packageCache.uid)
        }
        if (!performed) return
        currentPackages.forEachIndexed { index, it ->
            if (it.uid == packageCache.uid) {
                adapter.notifyItemChanged(index, PayloadUpdateSelection(selected))
            }
        }
        saveSelectedApplications()
    }

    data class PayloadUpdateSelection(val selected: Boolean)

    inner class ApplicationAdapter(private var applicationList: List<PackageCache>) :
        RecyclerView.Adapter<ApplicationViewHolder>() {

        @SuppressLint("NotifyDataSetChanged")
        fun setApplicationList(applicationList: List<PackageCache>) {
            this.applicationList = applicationList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup, viewType: Int
        ): ApplicationViewHolder {
            return ApplicationViewHolder(
                ViewAppListItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }

        override fun getItemCount(): Int {
            return applicationList.size
        }

        override fun onBindViewHolder(
            holder: ApplicationViewHolder, position: Int
        ) {
            holder.bind(applicationList[position])
        }

        override fun onBindViewHolder(
            holder: ApplicationViewHolder, position: Int, payloads: MutableList<Any>
        ) {
            if (payloads.isEmpty()) {
                onBindViewHolder(holder, position)
                return
            }
            payloads.forEach {
                when (it) {
                    is PayloadUpdateSelection -> holder.updateSelection(it.selected)
                }
            }
        }
    }

    inner class ApplicationViewHolder(
        private val binding: ViewAppListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(packageCache: PackageCache) {
            binding.appIcon.setImageDrawable(packageCache.applicationIcon)
            binding.applicationLabel.text = packageCache.applicationLabel
            binding.packageName.text = "${packageCache.packageName} (${packageCache.uid})"
            binding.selected.isChecked = selectedUIDs.contains(packageCache.uid)
            binding.root.setOnClickListener {
                updateApplicationSelection(packageCache, !binding.selected.isChecked)
            }
            binding.root.setOnLongClickListener {
                val popup = PopupMenu(it.context, it)
                popup.setForceShowIcon(true)
                popup.gravity = Gravity.END
                popup.menuInflater.inflate(R.menu.app_menu, popup.menu)
                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.action_copy_application_label -> {
                            clipboardText = packageCache.applicationLabel
                            true
                        }

                        R.id.action_copy_package_name -> {
                            clipboardText = packageCache.packageName
                            true
                        }

                        R.id.action_copy_uid -> {
                            clipboardText = packageCache.uid.toString()
                            true
                        }

                        else -> false
                    }
                }
                popup.show()
                true
            }
        }

        fun updateSelection(selected: Boolean) {
            binding.selected.isChecked = selected
        }
    }

    private fun searchApplications(searchText: String) {
        currentPackages = if (searchText.isEmpty()) {
            displayPackages
        } else {
            displayPackages.filter {
                it.applicationLabel.contains(
                    searchText, ignoreCase = true
                ) || it.packageName.contains(
                    searchText, ignoreCase = true
                ) || it.uid.toString().contains(searchText)
            }
        }
        adapter.setApplicationList(currentPackages)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.per_app_menu0, menu)

        if (menu != null) {
            val searchView = menu.findItem(R.id.action_search).actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    searchApplications(newText)
                    return true
                }
            })
            searchView.setOnCloseListener {
                searchApplications("")
                true
            }
            when (proxyMode) {
                Settings.PER_APP_PROXY_INCLUDE -> {
                    menu.findItem(R.id.action_mode_include).isChecked = true
                }

                Settings.PER_APP_PROXY_EXCLUDE -> {
                    menu.findItem(R.id.action_mode_exclude).isChecked = true
                }
            }
            when (sortMode) {
                SortMode.NAME -> {
                    menu.findItem(R.id.action_sort_by_name).isChecked = true
                }

                SortMode.PACKAGE_NAME -> {
                    menu.findItem(R.id.action_sort_by_package_name).isChecked = true
                }

                SortMode.UID -> {
                    menu.findItem(R.id.action_sort_by_uid).isChecked = true
                }

                SortMode.INSTALL_TIME -> {
                    menu.findItem(R.id.action_sort_by_install_time).isChecked = true
                }

                SortMode.UPDATE_TIME -> {
                    menu.findItem(R.id.action_sort_by_update_time).isChecked = true
                }
            }
            menu.findItem(R.id.action_sort_reverse).isChecked = sortReverse
            menu.findItem(R.id.action_hide_system_apps).isChecked = hideSystemApps
            menu.findItem(R.id.action_hide_offline_apps).isChecked = hideOfflineApps
            menu.findItem(R.id.action_hide_disabled_apps).isChecked = hideDisabledApps
        }

        return super.onCreateOptionsMenu(menu)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_mode_include -> {
                item.isChecked = true
                proxyMode = Settings.PER_APP_PROXY_INCLUDE
                binding.perAppProxyMode.setText(R.string.per_app_proxy_mode_include_description)
                lifecycleScope.launch {
                    Settings.perAppProxyMode = Settings.PER_APP_PROXY_INCLUDE
                }
            }

            R.id.action_mode_exclude -> {
                item.isChecked = true
                proxyMode = Settings.PER_APP_PROXY_EXCLUDE
                binding.perAppProxyMode.setText(R.string.per_app_proxy_mode_exclude_description)
                lifecycleScope.launch {
                    Settings.perAppProxyMode = Settings.PER_APP_PROXY_EXCLUDE
                }
            }

            R.id.action_sort_by_name -> {
                item.isChecked = true
                sortMode = SortMode.NAME
                filterApplicationList()
                adapter.setApplicationList(currentPackages)
            }

            R.id.action_sort_by_package_name -> {
                item.isChecked = true
                sortMode = SortMode.PACKAGE_NAME
                filterApplicationList()
                adapter.setApplicationList(currentPackages)
            }

            R.id.action_sort_by_uid -> {
                item.isChecked = true
                sortMode = SortMode.UID
                filterApplicationList()
                adapter.setApplicationList(currentPackages)
            }

            R.id.action_sort_by_install_time -> {
                item.isChecked = true
                sortMode = SortMode.INSTALL_TIME
                filterApplicationList()
                adapter.setApplicationList(currentPackages)
            }

            R.id.action_sort_by_update_time -> {
                item.isChecked = true
                sortMode = SortMode.UPDATE_TIME
                filterApplicationList()
                adapter.setApplicationList(currentPackages)
            }

            R.id.action_sort_reverse -> {
                item.isChecked = !item.isChecked
                sortReverse = item.isChecked
                filterApplicationList()
                adapter.setApplicationList(currentPackages)
            }

            R.id.action_hide_system_apps -> {
                item.isChecked = !item.isChecked
                hideSystemApps = item.isChecked
                filterApplicationList()
                adapter.setApplicationList(currentPackages)
            }

            R.id.action_hide_offline_apps -> {
                item.isChecked = !item.isChecked
                hideOfflineApps = item.isChecked
                filterApplicationList()
                adapter.setApplicationList(currentPackages)
            }

            R.id.action_hide_disabled_apps -> {
                item.isChecked = !item.isChecked
                hideDisabledApps = item.isChecked
                filterApplicationList()
                adapter.setApplicationList(currentPackages)
            }

            R.id.action_select_all -> {
                val selectedUIDs = mutableSetOf<Int>()
                for (packageCache in packages) {
                    selectedUIDs.add(packageCache.uid)
                }
                this.selectedUIDs = selectedUIDs
                saveSelectedApplications()
            }

            R.id.action_deselect_all -> {
                selectedUIDs = mutableSetOf()
                saveSelectedApplications()
            }

            R.id.action_export -> {
                lifecycleScope.launch {
                    val packageList = mutableListOf<String>()
                    for (packageCache in packages) {
                        if (selectedUIDs.contains(packageCache.uid)) {
                            packageList.add(packageCache.packageName)
                        }
                    }
                    clipboardText = packageList.joinToString("\n")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@PerAppProxyActivity0,
                            R.string.toast_copied_to_clipboard,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            R.id.action_import -> {
                val packageNames = clipboardText?.split("\n")?.distinct()
                    ?.takeIf { it.isNotEmpty() && it[0].isNotEmpty() }
                if (packageNames.isNullOrEmpty()) {
                    Toast.makeText(
                        this@PerAppProxyActivity0,
                        R.string.toast_clipboard_empty,
                        Toast.LENGTH_SHORT
                    ).show()
                    return true
                }
                val selectedUIDs = mutableSetOf<Int>()
                for (packageCache in packages) {
                    if (packageNames.contains(packageCache.packageName)) {
                        selectedUIDs.add(packageCache.uid)
                    }
                }
                lifecycleScope.launch {
                    postSaveSelectedApplications(selectedUIDs)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@PerAppProxyActivity0,
                            R.string.toast_imported_from_clipboard,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            }

            R.id.action_scan_china_apps -> {
                scanChinaApps()
            }
        }
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun scanChinaApps() {
        val binding = DialogProgressbarBinding.inflate(layoutInflater)
        binding.progress.max = currentPackages.size
        binding.message.setText(R.string.message_scanning)
        val progress = MaterialAlertDialogBuilder(
            this, com.google.android.material.R.style.Theme_MaterialComponents_Dialog
        ).setView(binding.root).setCancelable(false).create()
        progress.show()
        lifecycleScope.launch {
            val foundApps = withContext(Dispatchers.IO) {
                mutableMapOf<String, PackageCache>().also { foundApps ->
                    currentPackages.forEachIndexed { index, it ->
                        if (scanChinaPackage(it.packageName)) {
                            foundApps[it.packageName] = it
                        }
                        withContext(Dispatchers.Main) {
                            binding.progress.progress = index + 1
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                progress.dismiss()
                if (foundApps.isEmpty()) {
                    MaterialAlertDialogBuilder(this@PerAppProxyActivity0).setTitle(R.string.title_scan_result)
                        .setMessage(R.string.message_scan_app_no_apps_found)
                        .setPositiveButton(R.string.ok, null).show()
                    return@withContext
                }
                val dialogContent =
                    getString(R.string.message_scan_app_found) + "\n\n" + foundApps.entries.joinToString(
                        "\n"
                    ) {
                        "${it.value.applicationLabel} (${it.key})"
                    }
                MaterialAlertDialogBuilder(this@PerAppProxyActivity0).setTitle(R.string.title_scan_result)
                    .setMessage(dialogContent)
                    .setPositiveButton(R.string.action_select) { dialog, _ ->
                        dialog.dismiss()
                        lifecycleScope.launch {
                            val selectedUIDs = selectedUIDs.toMutableSet()
                            foundApps.values.forEach {
                                selectedUIDs.add(it.uid)
                            }
                            postSaveSelectedApplications(selectedUIDs)
                        }
                    }.setNegativeButton(R.string.action_deselect) { dialog, _ ->
                        dialog.dismiss()
                        lifecycleScope.launch {
                            val selectedUIDs = selectedUIDs.toMutableSet()
                            foundApps.values.forEach {
                                selectedUIDs.remove(it.uid)
                            }
                            postSaveSelectedApplications(selectedUIDs)
                        }
                    }.setNeutralButton(android.R.string.cancel, null).show()
            }
        }

    }

    @SuppressLint("NotifyDataSetChanged")
    private suspend fun postSaveSelectedApplications(newUIDs: MutableSet<Int>) {
        filterApplicationList(newUIDs)
        withContext(Dispatchers.Main) {
            selectedUIDs = newUIDs
            adapter.notifyDataSetChanged()
        }
        val packageList = selectedUIDs.mapNotNull { uid ->
            packages.find { it.uid == uid }?.packageName
        }
        Settings.perAppProxyList = packageList.toSet()
    }

    private fun saveSelectedApplications() {
        lifecycleScope.launch {
            val packageList = selectedUIDs.mapNotNull { uid ->
                packages.find { it.uid == uid }?.packageName
            }
            Settings.perAppProxyList = packageList.toSet()
        }
    }

    companion object {

        private val chinaAppPrefixList by lazy {
            runCatching {
                Application.application.assets.open("prefix-china-apps.txt").reader().readLines()
            }.getOrNull() ?: emptyList()
        }

        private val chinaAppRegex by lazy {
            ("(" + chinaAppPrefixList.joinToString("|").replace(".", "\\.") + ").*").toRegex()
        }

        fun scanChinaPackage(packageName: String): Boolean {
            val packageManagerFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            } else {
                @Suppress("DEPRECATION") PackageManager.GET_UNINSTALLED_PACKAGES or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            }
            if (packageName.matches(chinaAppRegex)) {
                return true
            }
            try {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Application.packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(packageManagerFlags.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION") Application.packageManager.getPackageInfo(
                        packageName, packageManagerFlags
                    )
                }
                if (packageInfo.services?.find { it.name.matches(chinaAppRegex) } != null || packageInfo.activities?.find {
                        it.name.matches(
                            chinaAppRegex
                        )
                    } != null || packageInfo.receivers?.find { it.name.matches(chinaAppRegex) } != null || packageInfo.providers?.find {
                        it.name.matches(
                            chinaAppRegex
                        )
                    } != null) {
                    return true
                }
                ZipFile(File(packageInfo.applicationInfo.publicSourceDir)).use {
                    for (packageEntry in it.entries()) {
                        if (!(packageEntry.name.startsWith("classes") && packageEntry.name.endsWith(
                                ".dex"
                            ))
                        ) {
                            continue
                        }
                        if (packageEntry.size > 15000000) {
                            return true
                        }
                        val input = it.getInputStream(packageEntry).buffered()
                        val dexFile = try {
                            DexBackedDexFile.fromInputStream(null, input)
                        } catch (e: Exception) {
                            return false
                        }
                        for (clazz in dexFile.classes) {
                            val clazzName =
                                clazz.type.substring(1, clazz.type.length - 1).replace("/", ".")
                                    .replace("$", ".")
                            if (clazzName.matches(chinaAppRegex)) {
                                return true
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {
            }
            return false
        }
    }

}