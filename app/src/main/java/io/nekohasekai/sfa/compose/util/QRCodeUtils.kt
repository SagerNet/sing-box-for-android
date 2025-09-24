package io.nekohasekai.sfa.compose.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

suspend fun saveQRCodeToGallery(
    context: Context,
    bitmap: Bitmap,
    profileName: String,
) = withContext(Dispatchers.IO) {
    try {
        val filename = "SingBox_QR_${profileName}_${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 and above, use MediaStore
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SingBox")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

            val resolver = context.contentResolver
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            imageUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(io.nekohasekai.sfa.R.string.qr_code_saved_to_gallery),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } else {
            // For older Android versions
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val singboxDir = File(imagesDir, "SingBox")
            if (!singboxDir.exists()) {
                singboxDir.mkdirs()
            }

            val imageFile = File(singboxDir, filename)
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            // Notify gallery about the new image
            MediaStore.Images.Media.insertImage(
                context.contentResolver,
                imageFile.absolutePath,
                filename,
                "SingBox QR Code",
            )

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "QR code saved to gallery", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(io.nekohasekai.sfa.R.string.failed_to_save_qr_code, e.message),
                Toast.LENGTH_LONG,
            ).show()
            e.printStackTrace()
        }
    }
}

suspend fun shareQRCodeImage(
    context: Context,
    bitmap: Bitmap,
    profileName: String,
) = withContext(Dispatchers.IO) {
    try {
        // Save bitmap to cache directory
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "qr_${profileName}_${System.currentTimeMillis()}.png")

        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        // Get URI for the file
        val contentUri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.cache",
                file,
            )

        // Create share intent
        val shareIntent =
            Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    context.getString(io.nekohasekai.sfa.R.string.profile_qr_code_text, profileName),
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        withContext(Dispatchers.Main) {
            context.startActivity(
                Intent.createChooser(
                    shareIntent,
                    context.getString(io.nekohasekai.sfa.R.string.intent_share_qr_code),
                ),
            )
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(io.nekohasekai.sfa.R.string.failed_to_share_qr_code, e.message),
                Toast.LENGTH_LONG,
            ).show()
            e.printStackTrace()
        }
    }
}
