package io.nekohasekai.sfa.terminal

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

data class ImportedFont(
    val name: String,
    val path: String,
)

object ImportedFontStore {

    private fun fontsDir(context: Context): File = File(context.filesDir, "fonts").also { it.mkdirs() }

    fun listImportedFonts(context: Context): List<ImportedFont> {
        val dir = fontsDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension in listOf("ttf", "otf", "ttc", "otc") }
            ?.mapNotNull { file ->
                try {
                    Typeface.createFromFile(file)
                    ImportedFont(name = file.nameWithoutExtension, path = file.absolutePath)
                } catch (_: Exception) {
                    null
                }
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun importFont(context: Context, uri: Uri): ImportedFont? {
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(uri) ?: return null
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "font.ttf"
        val destFile = File(fontsDir(context), fileName)
        inputStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return try {
            Typeface.createFromFile(destFile)
            ImportedFont(name = destFile.nameWithoutExtension, path = destFile.absolutePath)
        } catch (_: Exception) {
            destFile.delete()
            null
        }
    }

    fun deleteFont(context: Context, name: String) {
        val dir = fontsDir(context)
        dir.listFiles()
            ?.filter { it.nameWithoutExtension == name }
            ?.forEach { it.delete() }
    }

    fun loadTypeface(path: String): Typeface? = try {
        Typeface.createFromFile(path)
    } catch (_: Exception) {
        null
    }
}
