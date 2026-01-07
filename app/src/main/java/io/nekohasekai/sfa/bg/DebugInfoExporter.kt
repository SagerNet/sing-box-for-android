package io.nekohasekai.sfa.bg

import android.content.Context
import android.util.Log
import io.nekohasekai.sfa.utils.HookErrorClient
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DebugInfoExporter {
    private const val TAG = "DebugInfoExporter"

    fun export(context: Context, outputPath: String, packageName: String): String {
        Log.i(TAG, "export start: output=$outputPath, package=$packageName")
        val outFile = File(outputPath)
        if (!outFile.name.lowercase(Locale.US).endsWith(".zip")) {
            Log.e(TAG, "export failed: output path must end with .zip")
            throw IllegalArgumentException("output path must end with .zip")
        }
        val parent = outFile.parentFile!!
        if (!parent.exists()) {
            Log.i(TAG, "creating output directory: ${parent.path}")
            if (!parent.mkdirs()) {
                Log.e(TAG, "export failed: failed to create output directory: ${parent.path}")
                throw IllegalStateException("failed to create output directory")
            }
        }
        val warnings = mutableListOf<String>()
        var entriesAdded = 0
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zip ->
                Log.i(TAG, "adding export_info.txt")
                addTextEntry(zip, "system/export_info.txt", buildExportInfo(context, packageName))
                entriesAdded++
                Log.i(TAG, "adding framework entries")
                val frameworkCount = addFrameworkEntries(zip, warnings)
                entriesAdded += frameworkCount
                Log.i(TAG, "added $frameworkCount framework entries")
                Log.i(TAG, "adding apex entries")
                val apexCount = addApexEntries(zip, warnings)
                entriesAdded += apexCount
                Log.i(TAG, "added $apexCount apex entries")
                Log.i(TAG, "adding log entries")
                val logCount = addLogEntries(zip, warnings, context)
                entriesAdded += logCount
                Log.i(TAG, "added $logCount log entries")
                Log.i(TAG, "adding system entries")
                val systemCount = addSystemEntries(zip, warnings, packageName)
                entriesAdded += systemCount
                Log.i(TAG, "added $systemCount system entries")
                if (warnings.isNotEmpty()) {
                    addTextEntry(zip, "logs/debug_export.txt", warnings.joinToString("\n"))
                    entriesAdded++
                }
            }
            Log.i(TAG, "zip closed, total entries: $entriesAdded, file size: ${outFile.length()}")
        } catch (e: Throwable) {
            outFile.delete()
            val error = buildError("zip", "export failed", e, warnings, outputPath)
            Log.e(TAG, error, e)
            throw e
        }
        if (outFile.length() == 0L) {
            val error = "output file is empty after writing $entriesAdded entries"
            Log.e(TAG, error)
            outFile.delete()
            throw IllegalStateException(error)
        }
        outFile.setReadable(true, false)
        if (warnings.isNotEmpty()) {
            Log.w(TAG, "export finished with ${warnings.size} warnings, output size: ${outFile.length()}")
        } else {
            Log.i(TAG, "export finished: output=$outputPath, size=${outFile.length()}")
        }
        return outFile.absolutePath
    }

    private fun buildExportInfo(context: Context, packageName: String): String {
        val sb = StringBuilder()
        sb.append("package=").append(packageName).append('\n')
        sb.append("timestamp=").append(System.currentTimeMillis()).append('\n')
        sb.append("context_class=").append(context.javaClass.name).append('\n')
        return sb.toString()
    }

    private fun addFrameworkEntries(zip: ZipOutputStream, warnings: MutableList<String>): Int {
        var count = 0
        val roots =
            listOf(
                File("/system/framework"),
                File("/system_ext/framework"),
                File("/product/framework"),
                File("/vendor/framework"),
            )
        val targetFiles = setOf("framework.jar", "services.jar")
        for (root in roots) {
            if (!root.isDirectory) continue
            val destPrefix = "framework/${root.name}"
            val files = root.listFiles() ?: emptyArray()
            for (file in files) {
                if (!file.isFile) continue
                if (file.name !in targetFiles) continue
                if (addFileEntry(zip, file, "$destPrefix/${file.name}", warnings)) {
                    count++
                }
            }
        }
        return count
    }

    private fun addApexEntries(zip: ZipOutputStream, warnings: MutableList<String>): Int {
        var count = 0
        val tetheringApex = File("/apex/com.android.tethering/javalib")
        if (!tetheringApex.isDirectory) return 0
        val destPrefix = "framework/apex_com.android.tethering"
        val files = tetheringApex.listFiles() ?: emptyArray()
        for (file in files) {
            if (!file.isFile) continue
            if (!file.name.lowercase(Locale.US).endsWith(".jar")) continue
            if (addFileEntry(zip, file, "$destPrefix/${file.name}", warnings)) {
                count++
            }
        }
        return count
    }

    private fun addLogEntries(
        zip: ZipOutputStream,
        warnings: MutableList<String>,
        context: Context,
    ): Int {
        var count = 0
        if (streamCommandToZip(zip, "logs/logcat.txt", warnings, listOf("logcat", "-d", "-b", "all")) != null) count++
        if (streamCommandToZip(zip, "logs/dmesg.txt", warnings, listOf("dmesg")) != null) count++
        val serviceLogsResult = HookErrorClient.query(context)
        if (serviceLogsResult.logs.isNotEmpty()) {
            val formatted = formatLogEntries(serviceLogsResult.logs)
            addTextEntry(zip, "logs/service_logs.txt", formatted)
            count++
        } else if (serviceLogsResult.failure != null) {
            warnings.add("service logs: ${serviceLogsResult.failure}${serviceLogsResult.detail?.let { " ($it)" } ?: ""}")
        }
        val lspdDir = File("/data/adb/lspd/log")
        if (lspdDir.isDirectory) {
            val files = lspdDir.listFiles() ?: emptyArray()
            for (file in files) {
                if (!file.isFile) continue
                if (addFileEntry(zip, file, "logs/lspd/${file.name}", warnings)) count++
            }
        } else {
            warnings.add("lspd logs not found: /data/adb/lspd/log")
        }
        return count
    }

    private fun formatLogEntries(entries: List<LogEntry>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return entries.joinToString("\n---\n") { entry ->
            val levelName = when (entry.level) {
                LogEntry.LEVEL_DEBUG -> "DEBUG"
                LogEntry.LEVEL_INFO -> "INFO"
                LogEntry.LEVEL_WARN -> "WARN"
                LogEntry.LEVEL_ERROR -> "ERROR"
                else -> "UNKNOWN"
            }
            val timestamp = dateFormat.format(Date(entry.timestamp))
            buildString {
                append(levelName).append("[").append(timestamp).append("] ")
                append("[").append(entry.source).append("]: ")
                append(entry.message)
                if (!entry.stackTrace.isNullOrEmpty()) {
                    append("\n").append(entry.stackTrace)
                }
            }
        }
    }

    private fun addSystemEntries(
        zip: ZipOutputStream,
        warnings: MutableList<String>,
        packageName: String,
    ): Int {
        var count = 0
        if (streamCommandToZip(zip, "system/getprop.txt", warnings, listOf("getprop")) != null) count++
        if (streamCommandToZip(zip, "system/uname.txt", warnings, listOf("uname", "-a")) != null) count++
        if (streamCommandToZip(zip, "system/id.txt", warnings, listOf("id")) != null) count++
        if (addFileEntry(zip, File("/proc/version"), "system/proc_version.txt", warnings)) count++
        if (addFileEntry(zip, File("/proc/cpuinfo"), "system/cpuinfo.txt", warnings)) count++
        if (addFileEntry(zip, File("/proc/meminfo"), "system/meminfo.txt", warnings)) count++
        if (addFileEntry(zip, File("/proc/pressure/cpu"), "system/pressure_cpu.txt", warnings)) count++
        if (addFileEntry(zip, File("/proc/pressure/memory"), "system/pressure_memory.txt", warnings)) count++
        if (addFileEntry(zip, File("/proc/pressure/io"), "system/pressure_io.txt", warnings)) count++
        val cmdPackages =
            streamCommandToZip(
                zip,
                "system/packages_cmd.txt",
                warnings,
                listOf("cmd", "package", "list", "packages", "-f"),
            )
        if (cmdPackages != null) count++
        if ((cmdPackages == null || cmdPackages.bytes == 0L) && (cmdPackages?.exitCode ?: 1) != 0) {
            if (streamCommandToZip(
                zip,
                "system/packages_pm.txt",
                warnings,
                listOf("pm", "list", "packages", "-f"),
            ) != null) count++
        }
        if (streamCommandToZip(
            zip,
            "system/dumpsys_package_${packageName}.txt",
            warnings,
            listOf("dumpsys", "package", packageName),
        ) != null) count++
        return count
    }

    private fun addFileEntry(
        zip: ZipOutputStream,
        file: File,
        entryName: String,
        warnings: MutableList<String>,
    ): Boolean {
        if (!file.isFile) {
            warnings.add("missing file: ${file.path}")
            return false
        }
        try {
            val entry = ZipEntry(entryName)
            zip.putNextEntry(entry)
            BufferedInputStream(FileInputStream(file)).use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    zip.write(buffer, 0, read)
                }
            }
            zip.closeEntry()
            return true
        } catch (e: Throwable) {
            warnings.add("zip failed ${file.path}: ${e.message}")
            return false
        }
    }

    private fun addTextEntry(zip: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zip.putNextEntry(entry)
        val bytes = content.toByteArray()
        zip.write(bytes)
        zip.closeEntry()
    }

    private data class CommandResult(
        val exitCode: Int,
        val bytes: Long,
    )

    private fun streamCommandToZip(
        zip: ZipOutputStream,
        entryName: String,
        warnings: MutableList<String>,
        command: List<String>,
    ): CommandResult? {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val entry = ZipEntry(entryName)
            zip.putNextEntry(entry)
            var bytes = 0L
            process.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    zip.write(buffer, 0, read)
                    bytes += read
                }
            }
            zip.closeEntry()
            val code = process.waitFor()
            if (code != 0) {
                warnings.add("command failed (${command.joinToString(" ")}): exit=$code")
            }
            CommandResult(code, bytes)
        } catch (e: Throwable) {
            warnings.add("command failed (${command.joinToString(" ")}): ${e.message}")
            runCatching { zip.closeEntry() }
            null
        }
    }

    private fun buildError(
        stage: String,
        detail: String,
        throwable: Throwable?,
        warnings: List<String>,
        outputPath: String?,
    ): String {
        val sb = StringBuilder()
        sb.append("stage=").append(stage).append('\n')
        if (!outputPath.isNullOrBlank()) {
            sb.append("output=").append(outputPath).append('\n')
        }
        if (detail.isNotBlank()) {
            sb.append("detail=").append(detail).append('\n')
        }
        if (throwable != null) {
            sb.append("exception=").append(throwable.javaClass.name)
                .append(": ").append(throwable.message ?: "").append('\n')
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            sb.append(sw.toString())
        }
        if (warnings.isNotEmpty()) {
            if (!sb.endsWith('\n')) sb.append('\n')
            sb.append("warnings:\n").append(warnings.joinToString("\n"))
        }
        return sb.toString().trimEnd()
    }
}
