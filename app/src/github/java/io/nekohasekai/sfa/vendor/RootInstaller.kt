package io.nekohasekai.sfa.vendor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object RootInstaller {

    suspend fun checkAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su -c echo test")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun install(apkFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm install -r \"${apkFile.absolutePath}\""))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.contains("Success")) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Installation failed: $output"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
