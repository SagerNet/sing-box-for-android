package io.nekohasekai.sfa.bg

import android.content.pm.PackageManager
import android.os.Process
import io.nekohasekai.sfa.BuildConfig
import java.io.File

data class ResolvedUser(
    val packageName: String,
    val uid: Int,
    val gid: Int,
    val homeDir: String,
)

object UserResolver {

    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    const val TERMUX_HOME = "/data/data/com.termux/files/home"

    fun resolve(pm: PackageManager, username: String): ResolvedUser = when (username) {
        "root" -> ResolvedUser("root", Process.ROOT_UID, Process.ROOT_UID, "/")
        "shell" -> ResolvedUser("shell", Process.SHELL_UID, Process.SHELL_UID, "/data/local")
        "termux" -> resolvePackage(pm, TERMUX_PACKAGE)
        "sing-box" -> resolvePackage(pm, BuildConfig.APPLICATION_ID)
        else -> resolvePackage(pm, username)
    }

    private fun resolvePackage(pm: PackageManager, packageName: String): ResolvedUser {
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val homeDir = when (packageName) {
            TERMUX_PACKAGE -> TERMUX_HOME
            else -> appInfo.dataDir
        }
        return ResolvedUser(packageName, appInfo.uid, appInfo.uid, homeDir)
    }

    fun findShell(resolved: ResolvedUser): String {
        if (resolved.packageName == TERMUX_PACKAGE) {
            return findTermuxShell(
                File(TERMUX_PREFIX),
                resolved.homeDir,
            )
        }
        return "/system/bin/sh"
    }

    fun findTermuxShell(prefix: File, homeDir: String): String {
        val dotTermuxShell = File(homeDir, ".termux/shell")
        if (dotTermuxShell.canExecute()) {
            return dotTermuxShell.canonicalPath
        }
        val binDir = File(prefix, "bin")
        for (name in arrayOf("bash", "zsh", "fish", "sh")) {
            val candidate = File(binDir, name)
            if (candidate.canExecute()) return candidate.absolutePath
        }
        return "/system/bin/sh"
    }
}
