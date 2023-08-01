package io.nekohasekai.sfa.ui.profileoverride

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.getSystemService
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.ActivityPerAppProxyBinding
import io.nekohasekai.sfa.databinding.ViewAppListItemBinding
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import java.io.File
import java.util.zip.ZipFile

class PerAppProxyActivity : AbstractActivity() {


    private lateinit var binding: ActivityPerAppProxyBinding
    private lateinit var adapter: AppListAdapter

    private val perAppProxyList = mutableSetOf<String>()
    private val appList = mutableListOf<AppItem>()

    private var hideSystem = false
    private var searchKeyword = ""
    private val filteredAppList = mutableListOf<AppItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.title_per_app_proxy)
        binding = ActivityPerAppProxyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val proxyMode = Settings.perAppProxyMode
        if (proxyMode == Settings.PER_APP_PROXY_INCLUDE) {
            binding.radioPerAppInclude.isChecked = true
        } else {
            binding.radioPerAppExclude.isChecked = true
        }
        binding.radioGroupPerAppMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radio_per_app_include) {
                Settings.perAppProxyMode = Settings.PER_APP_PROXY_INCLUDE
            } else {
                Settings.perAppProxyMode = Settings.PER_APP_PROXY_EXCLUDE
            }
        }

        perAppProxyList.addAll(Settings.perAppProxyList.toMutableSet())
        adapter = AppListAdapter(filteredAppList) {
            val item = filteredAppList[it]
            if (item.selected) {
                perAppProxyList.add(item.packageName)
            } else {
                perAppProxyList.remove(item.packageName)
            }
            Settings.perAppProxyList = perAppProxyList
        }
        binding.recyclerViewAppList.adapter = adapter
        loadAppList()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadAppList() {
        binding.recyclerViewAppList.isGone = true
        binding.layoutProgress.isGone = false

        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    PackageManager.GET_PERMISSIONS or PackageManager.MATCH_UNINSTALLED_PACKAGES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_PERMISSIONS or PackageManager.GET_UNINSTALLED_PACKAGES
                }
                val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledPackages(PackageInfoFlags.of(flag.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledPackages(flag)
                }
                val list = mutableListOf<AppItem>()
                installedPackages.forEach {
                    if (it.packageName != packageName &&
                        (it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                                || it.packageName == "android")
                    ) {
                        list.add(
                            AppItem(
                                it.packageName,
                                it.applicationInfo.loadLabel(packageManager).toString(),
                                it.applicationInfo.loadIcon(packageManager),
                                it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 1,
                                perAppProxyList.contains(it.packageName)
                            )
                        )
                    }
                }
                list.sortedWith(compareBy({ !it.selected }, { it.name }))
            }
            appList.clear()
            appList.addAll(list)

            perAppProxyList.toSet().forEach {
                if (appList.find { app -> app.packageName == it } == null) {
                    perAppProxyList.remove(it)
                }
            }
            Settings.perAppProxyList = perAppProxyList

            filteredAppList.clear()
            if (hideSystem) {
                filteredAppList.addAll(appList.filter { !it.isSystemApp })
            } else {
                filteredAppList.addAll(appList)
            }
            adapter.notifyDataSetChanged()

            binding.recyclerViewAppList.scrollToPosition(0)
            binding.layoutProgress.isGone = true
            binding.recyclerViewAppList.isGone = false
        }
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
                    searchKeyword = newText
                    filterApps()
                    return true
                }
            })
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_hide_system -> {
                hideSystem = !hideSystem
                if (hideSystem) {
                    item.setTitle(R.string.menu_show_system)
                } else {
                    item.setTitle(R.string.menu_hide_system)
                }
                filterApps()
                return true
            }

            R.id.action_import -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.menu_import_from_clipboard)
                    .setMessage(R.string.message_import_from_clipboard)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        importFromClipboard()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                return true
            }

            R.id.action_export -> {
                exportToClipboard()
                return true
            }

            R.id.action_scan_china_apps -> {
                scanChinaApps()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterApps() {
        filteredAppList.clear()
        if (searchKeyword.isNotEmpty()) {
            filteredAppList.addAll(appList.filter {
                (!hideSystem || !it.isSystemApp) &&
                        (it.name.contains(searchKeyword, true)
                                || it.packageName.contains(searchKeyword, true))
            })
            adapter.notifyDataSetChanged()
        } else if (hideSystem) {
            filteredAppList.addAll(appList.filter { !it.isSystemApp })
        } else {
            filteredAppList.addAll(appList)
        }
        adapter.notifyDataSetChanged()
    }

    private fun importFromClipboard() {
        val clipboardManager = getSystemService<ClipboardManager>()!!
        if (!clipboardManager.hasPrimaryClip()) {
            Toast.makeText(this, R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val content = clipboardManager.primaryClip?.getItemAt(0)?.text?.split("\n")?.distinct()
        if (content.isNullOrEmpty()) {
            Toast.makeText(this, R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        perAppProxyList.clear()
        perAppProxyList.addAll(content)
        loadAppList()
        Toast.makeText(this, R.string.toast_imported_from_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun exportToClipboard() {
        if (perAppProxyList.isEmpty()) {
            Toast.makeText(this, R.string.toast_app_list_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val content = perAppProxyList.joinToString("\n")
        val clipboardManager = getSystemService<ClipboardManager>()!!
        val clip = ClipData.newPlainText(null, content)
        clipboardManager.setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanChinaApps() {
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setView(R.layout.dialog_progress)
            .setCancelable(false)
            .create()
        progressDialog.setOnShowListener {
            val dialog = it as Dialog
            dialog.findViewById<TextView>(R.id.text_message).setText(R.string.message_scanning)
        }
        progressDialog.show()

        lifecycleScope.launch {
            val scanResult = withContext(Dispatchers.IO) {
                val appNameMap = mutableMapOf<String, String>()
                appList.forEach {
                    appNameMap[it.packageName] = it.name
                }
                val foundChinaApps = mutableMapOf<String, String>()
                scanChinaApps(appList.map { it.packageName }).forEach { packageName ->
                    foundChinaApps[packageName] = appNameMap[packageName] ?: "Unknown"
                }
                foundChinaApps
            }
            progressDialog.dismiss()

            if (scanResult.isEmpty()) {
                MaterialAlertDialogBuilder(this@PerAppProxyActivity)
                    .setTitle(R.string.message)
                    .setMessage(R.string.message_scan_app_no_apps_found)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }

            val dialogContent = getString(R.string.message_scan_app_found) + "\n\n" +
                    scanResult.entries.joinToString("\n") {
                        "${it.value} (${it.key})"
                    }
            MaterialAlertDialogBuilder(this@PerAppProxyActivity)
                .setTitle(R.string.title_scan_result)
                .setMessage(dialogContent)
                .setPositiveButton(R.string.action_select) { dialog, _ ->
                    perAppProxyList.addAll(scanResult.keys)
                    Settings.perAppProxyList = perAppProxyList
                    loadAppList()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.action_deselect) { dialog, _ ->
                    perAppProxyList.removeAll(scanResult.keys)
                    Settings.perAppProxyList = perAppProxyList
                    loadAppList()
                    dialog.dismiss()
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        }
    }

    companion object {
        private const val TAG = "PerAppProxyActivity"

        fun scanChinaApps(packageNameList: List<String>): List<String> {
            val chinaAppPrefixList = try {
                Application.application.assets.open("prefix-china-apps.txt").reader().readLines()
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "scan china apps: failed to load prefix from assets, error = ${e.message}"
                )
                null
            }
            if (chinaAppPrefixList.isNullOrEmpty()) {
                return listOf()
            }
            val chinaAppRegex =
                ("(" + chinaAppPrefixList.joinToString("|").replace(".", "\\.") + ").*").toRegex()
            val packageManagerFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES or
                        PackageManager.GET_ACTIVITIES or
                        PackageManager.GET_SERVICES or
                        PackageManager.GET_RECEIVERS or
                        PackageManager.GET_PROVIDERS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_UNINSTALLED_PACKAGES or
                        PackageManager.GET_ACTIVITIES or
                        PackageManager.GET_SERVICES or
                        PackageManager.GET_RECEIVERS or
                        PackageManager.GET_PROVIDERS
            }
            val foundChinaApps = mutableListOf<String>()
            for (packageName in packageNameList) {
                if (packageName == "android") {
                    continue
                }
                if (packageName.matches(chinaAppRegex)) {
                    foundChinaApps.add(packageName)
                    continue
                }

                try {
                    val packageInfo =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Application.packageManager.getPackageInfo(
                                packageName,
                                PackageInfoFlags.of(packageManagerFlags.toLong())
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            Application.packageManager.getPackageInfo(
                                packageName,
                                packageManagerFlags
                            )
                        }

                    if (packageInfo.services?.find { it.name.matches(chinaAppRegex) } != null
                        || packageInfo.activities?.find { it.name.matches(chinaAppRegex) } != null
                        || packageInfo.receivers?.find { it.name.matches(chinaAppRegex) } != null
                        || packageInfo.providers?.find { it.name.matches(chinaAppRegex) } != null) {
                        foundChinaApps.add(packageName)
                        continue
                    }
                    val packageFile = ZipFile(File(packageInfo.applicationInfo.publicSourceDir))
                    for (packageEntry in packageFile.entries()) {
                        if (!(packageEntry.name.startsWith("classes") && packageEntry.name.endsWith(
                                ".dex"
                            ))
                        ) {
                            continue
                        }
                        if (packageEntry.size > 15000000) {
                            foundChinaApps.add(packageName)
                            break
                        }
                        val input = packageFile.getInputStream(packageEntry).buffered()
                        val dexFile = try {
                            DexBackedDexFile.fromInputStream(null, input)
                        } catch (e: Exception) {
                            foundChinaApps.add(packageName)
                            Log.w(
                                TAG,
                                "scan china apps: failed to read dex file, error = ${e.message}"
                            )
                            break
                        }
                        for (clazz in dexFile.classes) {
                            val clazzName = clazz.type.substring(1, clazz.type.length - 1)
                                .replace("/", ".")
                                .replace("$", ".")
                            if (clazzName.matches(chinaAppRegex)) {
                                foundChinaApps.add(packageName)
                                break
                            }
                        }
                    }
                    packageFile.close()
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "scan china apps: something went wrong when scanning package ${packageName}, error = ${e.message}"
                    )
                    continue
                }
                System.gc()
            }
            return foundChinaApps
        }
    }

    data class AppItem(
        val packageName: String,
        val name: String,
        val icon: Drawable,
        val isSystemApp: Boolean,
        var selected: Boolean
    )

    class AppListAdapter(
        private val list: List<AppItem>,
        private val onSelectChange: (Int) -> Unit
    ) :
        RecyclerView.Adapter<AppListViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppListViewHolder {
            val binding =
                ViewAppListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return AppListViewHolder(binding)
        }

        override fun getItemCount(): Int {
            return list.size
        }

        override fun onBindViewHolder(holder: AppListViewHolder, position: Int) {
            val item = list[position]
            holder.bind(item)
            holder.itemView.setOnClickListener {
                item.selected = !item.selected
                onSelectChange.invoke(position)
                notifyItemChanged(position, "check")
            }
        }

        override fun onBindViewHolder(
            holder: AppListViewHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
            if (payloads.contains("check")) {
                holder.bindCheck(list[position])
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

    }

    class AppListViewHolder(
        private val binding: ViewAppListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppItem) {
            binding.imageAppIcon.setImageDrawable(item.icon)
            binding.textAppName.text = item.name
            binding.textAppPackageName.text = item.packageName
            binding.checkboxAppSelected.isChecked = item.selected
        }

        fun bindCheck(item: AppItem) {
            binding.checkboxAppSelected.isChecked = item.selected
        }
    }
}