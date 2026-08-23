package io.nekohasekai.sfa.bg

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PowerReport(
    val id: String,
    val date: Date,
    val directory: File,
    val isRead: Boolean,
)

data class PowerReportFile(
    val kind: Kind,
    val displayName: String,
    val file: File,
) {
    enum class Kind {
        METADATA,
        CONFIG,
        TIMELINE,
        EVENTS,
        GO_LOG,
        PROFILE,
    }
}

object PowerReportManager {
    private const val METADATA_FILE_NAME = "metadata.json"
    private const val CONFIG_FILE_NAME = "configuration.json"
    private const val TIMELINE_FILE_NAME = "timeline.jsonl"
    private const val EVENTS_FILE_NAME = "events.jsonl"
    private const val GO_LOG_FILE_NAME = "go.log"
    private const val READ_MARKER_FILE_NAME = ".read"
    private const val POWER_REPORTS_DIR_NAME = "power_reports"
    private const val CONTENT_TAIL_LIMIT = 512L * 1024L

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private lateinit var workingDir: File

    private val _reports = MutableStateFlow<List<PowerReport>>(emptyList())
    val reports: StateFlow<List<PowerReport>> = _reports
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

    private fun scanReports(): List<PowerReport> {
        val reportsDir = File(workingDir, POWER_REPORTS_DIR_NAME)
        if (!reportsDir.isDirectory) return emptyList()
        val directories = reportsDir.listFiles { file -> file.isDirectory } ?: return emptyList()
        return directories.mapNotNull { dir ->
            val date = parseTimestamp(dir.name) ?: return@mapNotNull null
            PowerReport(
                id = dir.name,
                date = date,
                directory = dir,
                isRead = File(dir, READ_MARKER_FILE_NAME).exists(),
            )
        }.sortedByDescending { it.date }
    }

    fun availableFiles(report: PowerReport): List<PowerReportFile> {
        val files = mutableListOf<PowerReportFile>()
        val namedFiles = listOf(
            Triple(METADATA_FILE_NAME, PowerReportFile.Kind.METADATA, "Metadata"),
            Triple(CONFIG_FILE_NAME, PowerReportFile.Kind.CONFIG, "Configuration"),
            Triple(TIMELINE_FILE_NAME, PowerReportFile.Kind.TIMELINE, TIMELINE_FILE_NAME),
            Triple(EVENTS_FILE_NAME, PowerReportFile.Kind.EVENTS, EVENTS_FILE_NAME),
            Triple(GO_LOG_FILE_NAME, PowerReportFile.Kind.GO_LOG, "Log"),
        )
        for ((name, kind, displayName) in namedFiles) {
            val file = File(report.directory, name)
            if (file.exists()) {
                files.add(PowerReportFile(kind, displayName, file))
            }
        }
        val namedFileNames = namedFiles.map { it.first }
        report.directory.listFiles()?.filter { file ->
            file.isFile &&
                file.name !in namedFileNames &&
                file.name != READ_MARKER_FILE_NAME
        }?.sortedBy { it.name }?.forEach { file ->
            files.add(PowerReportFile(PowerReportFile.Kind.PROFILE, file.name, file))
        }
        return files
    }

    fun loadFileContent(file: PowerReportFile): String {
        if (!file.file.exists()) return ""
        val length = file.file.length()
        val content = if (length > CONTENT_TAIL_LIMIT) {
            RandomAccessFile(file.file, "r").use { randomAccess ->
                randomAccess.seek(length - CONTENT_TAIL_LIMIT)
                val bytes = ByteArray(CONTENT_TAIL_LIMIT.toInt())
                randomAccess.readFully(bytes)
                "…\n" + String(bytes)
            }
        } else {
            file.file.readText()
        }
        if (file.kind == PowerReportFile.Kind.METADATA) {
            return runCatching {
                JSONObject(content).toString(2)
            }.getOrDefault(content)
        }
        return content
    }

    fun markAsRead(report: PowerReport) {
        File(report.directory, READ_MARKER_FILE_NAME).createNewFile()
        val updated = _reports.value.map {
            if (it.id == report.id) it.copy(isRead = true) else it
        }
        _reports.value = updated
        _unreadCount.value = updated.count { !it.isRead }
    }

    suspend fun delete(report: PowerReport) = withContext(Dispatchers.IO) {
        report.directory.deleteRecursively()
        val updated = _reports.value.filter { it.id != report.id }
        _reports.value = updated
        _unreadCount.value = updated.count { !it.isRead }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        File(workingDir, POWER_REPORTS_DIR_NAME).deleteRecursively()
        _reports.value = emptyList()
        _unreadCount.value = 0
    }

    fun hasConfigFile(report: PowerReport): Boolean = File(report.directory, CONFIG_FILE_NAME).exists()

    fun hasLogFile(report: PowerReport): Boolean = File(report.directory, GO_LOG_FILE_NAME).exists()

    suspend fun createZipArchive(report: PowerReport, includeConfig: Boolean, includeLog: Boolean, useAgeEncryption: Boolean): File = withContext(Dispatchers.IO) {
        val cacheDir = File(Application.application.cacheDir, POWER_REPORTS_DIR_NAME)
        cacheDir.mkdirs()
        val zipFile = File(cacheDir, if (useAgeEncryption) "${report.id}.zip.age" else "${report.id}.zip")
        zipFile.delete()
        val strippedDir = File(cacheDir, report.id)
        strippedDir.deleteRecursively()
        report.directory.copyRecursively(strippedDir, overwrite = true)
        File(strippedDir, READ_MARKER_FILE_NAME).delete()
        if (!includeConfig) {
            File(strippedDir, CONFIG_FILE_NAME).delete()
        }
        if (!includeLog) {
            File(strippedDir, GO_LOG_FILE_NAME).delete()
        }
        Libbox.createZipArchive(strippedDir.path, zipFile.path, useAgeEncryption)
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
