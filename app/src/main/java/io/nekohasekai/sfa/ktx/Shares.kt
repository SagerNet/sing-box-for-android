package io.nekohasekai.sfa.ktx

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.google.android.material.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.ProfileContent
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.ui.shared.QRCodeDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun Context.shareProfile(profile: Profile) {
    val content = ProfileContent()
    content.name = profile.name
    when (profile.typed.type) {
        TypedProfile.Type.Local -> {
            content.type = io.nekohasekai.libbox.Libbox.ProfileTypeLocal
        }

        TypedProfile.Type.Remote -> {
            content.type = io.nekohasekai.libbox.Libbox.ProfileTypeRemote
        }
    }
    content.config = File(profile.typed.path).readText()
    content.remotePath = profile.typed.remoteURL
    content.autoUpdate = profile.typed.autoUpdate
    content.autoUpdateInterval = profile.typed.autoUpdateInterval
    content.lastUpdated = profile.typed.lastUpdated.time

    val configDirectory = File(cacheDir, "share").also { it.mkdirs() }
    val profileFile = File(configDirectory, "${profile.name}.bpf")
    profileFile.writeBytes(content.encode())
    val uri = FileProvider.getUriForFile(this, "$packageName.cache", profileFile)
    withContext(Dispatchers.Main) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("application/octet-stream")
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_STREAM, uri),
                getString(R.string.abc_shareactionprovider_share_with)
            )
        )
    }
}

fun FragmentActivity.shareProfileURL(profile: Profile) {
    val link = Libbox.generateRemoteProfileImportLink(
        profile.name,
        profile.typed.remoteURL
    )
    val imageSize = dp2px(256)
    val color = getAttrColor(com.google.android.material.R.attr.colorPrimary)
    val image = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, imageSize, imageSize, null)
    val imageWidth = image.width
    val imageHeight = image.height
    val imageArray = IntArray(imageWidth * imageHeight)
    for (y in 0 until imageHeight) {
        val offset = y * imageWidth
        for (x in 0 until imageWidth) {
            imageArray[offset + x] = if (image.get(x, y)) color else Color.TRANSPARENT

        }
    }
    val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(imageArray, 0, imageSize, 0, 0, imageWidth, imageHeight)
    val dialog = QRCodeDialog(bitmap)
    dialog.show(supportFragmentManager, "share-profile-url")
}