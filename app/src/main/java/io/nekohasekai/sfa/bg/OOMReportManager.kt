package io.nekohasekai.sfa.bg

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class OOMReport(
    val id: String,
    val date: Date,
    val directory: File,
    val isRead: Boolean,
)

data class OOMReportFile(
    val kind: Kind,
    val displayName: String,
    val file: File,
) {
    enum class Kind {
        METADATA,
        CONFIG,
        PROFILE,
    }
}

object OOMReportManager {
    private const val METADATA_FILE_NAME = "metadata.json"
    private const val CONFIG_FILE_NAME = "configuration.json"
    private const val CMDLINE_FILE_NAME = "cmdline"
    private const val READ_MARKER_FILE_NAME = ".read"
    private const val OOM_REPORTS_DIR_NAME = "oom_reports"

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private lateinit var workingDir: File

    private val _reports = MutableStateFlow<List<OOMReport>>(emptyList())
    val reports: StateFlow<List<OOMReport>> = _reports
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    fun install(workingDir: File) {
        this.workingDir = workingDir
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val reports = scanReports()
        _reports.value = reports
        _unreadCount.value = reports.count { !it.isRead }
    }

    private fun scanReports(): List<OOMReport> {
        val reportsDir = File(workingDir, OOM_REPORTS_DIR_NAME)
        if (!reportsDir.isDirectory) return emptyList()
        val directories = reportsDir.listFiles { file -> file.isDirectory } ?: return emptyList()
        return directories.mapNotNull { dir ->
            val date = parseTimestamp(dir.name) ?: return@mapNotNull null
            OOMReport(
                id = dir.name,
                date = date,
                directory = dir,
                isRead = File(dir, READ_MARKER_FILE_NAME).exists(),
            )
        }.sortedByDescending { it.date }
    }

    fun availableFiles(report: OOMReport): List<OOMReportFile> {
        val files = mutableListOf<OOMReportFile>()
        val metadataFile = File(report.directory, METADATA_FILE_NAME)
        if (metadataFile.exists()) {
            files.add(OOMReportFile(OOMReportFile.Kind.METADATA, "Metadata", metadataFile))
        }
        val configFile = File(report.directory, CONFIG_FILE_NAME)
        if (configFile.exists()) {
            files.add(OOMReportFile(OOMReportFile.Kind.CONFIG, "Configuration", configFile))
        }
        report.directory.listFiles()?.filter { file ->
            file.isFile &&
                file.name != METADATA_FILE_NAME &&
                file.name != CONFIG_FILE_NAME &&
                file.name != CMDLINE_FILE_NAME &&
                file.name != READ_MARKER_FILE_NAME
        }?.sortedBy { it.name }?.forEach { file ->
            files.add(OOMReportFile(OOMReportFile.Kind.PROFILE, file.name, file))
        }
        return files
    }

    fun loadFileContent(file: OOMReportFile): String {
        if (!file.file.exists()) return ""
        val content = file.file.readText()
        if (file.kind == OOMReportFile.Kind.METADATA) {
            return runCatching {
                JSONObject(content).toString(2)
            }.getOrDefault(content)
        }
        return content
    }

    fun markAsRead(report: OOMReport) {
        File(report.directory, READ_MARKER_FILE_NAME).createNewFile()
        val updated = _reports.value.map {
            if (it.id == report.id) it.copy(isRead = true) else it
        }
        _reports.value = updated
        _unreadCount.value = updated.count { !it.isRead }
    }

    suspend fun delete(report: OOMReport) = withContext(Dispatchers.IO) {
        report.directory.deleteRecursively()
        val updated = _reports.value.filter { it.id != report.id }
        _reports.value = updated
        _unreadCount.value = updated.count { !it.isRead }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        File(workingDir, OOM_REPORTS_DIR_NAME).deleteRecursively()
        _reports.value = emptyList()
        _unreadCount.value = 0
    }

    fun hasConfigFile(report: OOMReport): Boolean = File(report.directory, CONFIG_FILE_NAME).exists()

    suspend fun createZipArchive(report: OOMReport, includeConfig: Boolean): File = withContext(Dispatchers.IO) {
        val cacheDir = File(Application.application.cacheDir, OOM_REPORTS_DIR_NAME)
        cacheDir.mkdirs()
        val zipFile = File(cacheDir, "${report.id}.zip")
        zipFile.delete()
        val strippedDir = File(cacheDir, report.id)
        strippedDir.deleteRecursively()
        report.directory.copyRecursively(strippedDir, overwrite = true)
        File(strippedDir, READ_MARKER_FILE_NAME).delete()
        if (!includeConfig) {
            File(strippedDir, CONFIG_FILE_NAME).delete()
        }
        Libbox.createZipArchive(strippedDir.path, zipFile.path)
        zipFile
    }

    private fun parseTimestamp(name: String): Date? {
        val components = name.split("-")
        val baseName = if (components.size > 5 && components.last().toIntOrNull() != null) {
            components.dropLast(1).joinToString("-")
        } else {
            name
        }
        return try {
            timestampFormat.parse(baseName)
        } catch (_: ParseException) {
            null
        }
    }
}
