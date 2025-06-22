package io.nekohasekai.sfa.ui.profileoverride

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.ActivityPerAppProxyBinding
import io.nekohasekai.sfa.databinding.DialogProgressbarBinding
import io.nekohasekai.sfa.databinding.ViewAppListItemBinding
import io.nekohasekai.sfa.ktx.clipboardText
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

class PerAppProxyActivity : AbstractActivity<ActivityPerAppProxyBinding>() {
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
        private val appInfo: ApplicationInfo,
    ) {

        val packageName: String get() = packageInfo.packageName

        val uid get() = packageInfo.applicationInfo!!.uid

        val installTime get() = packageInfo.firstInstallTime
        val updateTime get() = packageInfo.lastUpdateTime
        val isSystem get() = appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 1
        val isOffline get() = packageInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) != true
        val isDisabled get() = appInfo.flags and ApplicationInfo.FLAG_INSTALLED == 0

        val applicationIcon by lazy {
            appInfo.loadIcon(packageManager)
        }

        val applicationLabel by lazy {
            appInfo.loadLabel(packageManager).toString()
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

        ViewCompat.setOnApplyWindowInsetsListener(binding.appList) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                proxyMode = if (Settings.perAppProxyMode == Settings.PER_APP_PROXY_INCLUDE) {
                    Settings.PER_APP_PROXY_INCLUDE
                } else {
                    Settings.PER_APP_PROXY_EXCLUDE
                }
                withContext(Dispatchers.Main) {
                    if (proxyMode == Settings.PER_APP_PROXY_INCLUDE) {
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
    }

    private fun reloadApplicationList() {
        val packageManagerFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager.GET_PERMISSIONS or PackageManager.MATCH_UNINSTALLED_PACKAGES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_PERMISSIONS or PackageManager.GET_UNINSTALLED_PACKAGES
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
            val appInfo = packageInfo.applicationInfo ?: continue
            packages.add(PackageCache(packageInfo, appInfo))
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
        menuInflater.inflate(R.menu.per_app_menu, menu)

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
                currentPackages.forEach {
                    selectedUIDs.add(it.uid)
                }
                lifecycleScope.launch {
                    postSaveSelectedApplications(selectedUIDs)
                }
            }

            R.id.action_deselect_all -> {
                lifecycleScope.launch {
                    postSaveSelectedApplications(mutableSetOf())
                }
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
                            this@PerAppProxyActivity,
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
                        this@PerAppProxyActivity,
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
                            this@PerAppProxyActivity,
                            R.string.toast_imported_from_clipboard,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            }

            R.id.action_scan_china_apps -> {
                scanChinaApps()
            }

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun scanChinaApps() {
        val binding = DialogProgressbarBinding.inflate(layoutInflater)
        binding.progress.max = currentPackages.size
        binding.message.setText(R.string.message_scanning)
        val dialogTheme =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && resources.configuration.isNightModeActive) {
                com.google.android.material.R.style.Theme_MaterialComponents_Dialog
            } else {
                com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog
            }
        val progress = MaterialAlertDialogBuilder(
            this, dialogTheme
        ).setView(binding.root).setCancelable(false).create()
        progress.show()
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            val foundApps = withContext(Dispatchers.Default) {
                mutableMapOf<String, PackageCache>().also { foundApps ->
                    val progressInt = AtomicInteger()
                    currentPackages.map { it ->
                        async {
                            if (scanChinaPackage(it.packageName)) {
                                foundApps[it.packageName] = it
                            }
                            runOnUiThread {
                                binding.progress.progress = progressInt.addAndGet(1)
                            }
                        }
                    }.awaitAll()
                }
            }
            Log.d(
                "PerAppProxyActivity",
                "Scan China apps took ${(System.currentTimeMillis() - startTime).toDouble() / 1000}s"
            )
            withContext(Dispatchers.Main) {
                progress.dismiss()
                if (foundApps.isEmpty()) {
                    MaterialAlertDialogBuilder(this@PerAppProxyActivity).setTitle(R.string.title_scan_result)
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
                MaterialAlertDialogBuilder(this@PerAppProxyActivity).setTitle(R.string.title_scan_result)
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

        private val skipPrefixList = listOf(
            "com.google",
            "com.android.chrome",
            "com.android.vending",
            "com.microsoft",
            "com.apple",
            "com.zhiliaoapp.musically", // Banned by China
            "com.android.providers.downloads",
        )

        private val chinaAppPrefixList = listOf(
            "com.tencent",
            "com.alibaba",
            "com.umeng",
            "com.qihoo",
            "com.ali",
            "com.alipay",
            "com.amap",
            "com.sina",
            "com.weibo",
            "com.vivo",
            "com.xiaomi",
            "com.huawei",
            "com.taobao",
            "com.secneo",
            "s.h.e.l.l",
            "com.stub",
            "com.kiwisec",
            "com.secshell",
            "com.wrapper",
            "cn.securitystack",
            "com.mogosec",
            "com.secoen",
            "com.netease",
            "com.mx",
            "com.qq.e",
            "com.baidu",
            "com.bytedance",
            "com.bugly",
            "com.miui",
            "com.oppo",
            "com.coloros",
            "com.iqoo",
            "com.meizu",
            "com.gionee",
            "cn.nubia",
            "com.oplus",
            "andes.oplus",
            "com.unionpay",
            "cn.wps"
        )


        private val chinaAppRegex by lazy {
            ("(" + chinaAppPrefixList.joinToString("|").replace(".", "\\.") + ").*").toRegex()
        }

        fun scanChinaPackage(packageName: String): Boolean {
            skipPrefixList.forEach {
                if (packageName == it || packageName.startsWith("$it.")) return false
            }

            val packageManagerFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_UNINSTALLED_PACKAGES or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            }
            if (packageName.matches(chinaAppRegex)) {
                Log.d("PerAppProxyActivity", "Match package name: $packageName")
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
                val appInfo = packageInfo.applicationInfo ?: return false
                packageInfo.services?.forEach {
                    if (it.name.matches(chinaAppRegex)) {
                        Log.d("PerAppProxyActivity", "Match service ${it.name} in $packageName")
                        return true
                    }
                }
                packageInfo.activities?.forEach {
                    if (it.name.matches(chinaAppRegex)) {
                        Log.d("PerAppProxyActivity", "Match activity ${it.name} in $packageName")
                        return true
                    }
                }
                packageInfo.receivers?.forEach {
                    if (it.name.matches(chinaAppRegex)) {
                        Log.d("PerAppProxyActivity", "Match receiver ${it.name} in $packageName")
                        return true
                    }
                }
                packageInfo.providers?.forEach {
                    if (it.name.matches(chinaAppRegex)) {
                        Log.d("PerAppProxyActivity", "Match provider ${it.name} in $packageName")
                        return true
                    }
                }
                ZipFile(File(appInfo.publicSourceDir)).use {
                    for (packageEntry in it.entries()) {
                        if (packageEntry.name.startsWith("firebase-")) return false
                    }
                    for (packageEntry in it.entries()) {
                        if (!(packageEntry.name.startsWith("classes") && packageEntry.name.endsWith(
                                ".dex"
                            ))
                        ) {
                            continue
                        }
                        if (packageEntry.size > 15000000) {
                            Log.d(
                                "PerAppProxyActivity",
                                "Confirm $packageName due to large dex file"
                            )
                            return true
                        }
                        val input = it.getInputStream(packageEntry).buffered()
                        val dexFile = try {
                            DexBackedDexFile.fromInputStream(null, input)
                        } catch (e: Exception) {
                            Log.e("PerAppProxyActivity", "Error reading dex file", e)
                            return false
                        }
                        for (clazz in dexFile.classes) {
                            val clazzName =
                                clazz.type.substring(1, clazz.type.length - 1).replace("/", ".")
                                    .replace("$", ".")
                            if (clazzName.matches(chinaAppRegex)) {
                                Log.d("PerAppProxyActivity", "Match $clazzName in $packageName")
                                return true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PerAppProxyActivity", "Error scanning package $packageName", e)
            }
            return false
        }
    }

}
