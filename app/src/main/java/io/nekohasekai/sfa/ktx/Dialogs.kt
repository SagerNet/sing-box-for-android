package io.nekohasekai.sfa.ktx

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sfa.R

fun Context.errorDialogBuilder(
    @StringRes messageId: Int,
): MaterialAlertDialogBuilder {
    return errorDialogBuilder(getString(messageId))
}

fun Context.errorDialogBuilder(message: String): MaterialAlertDialogBuilder {
    val contentView = buildSelectableMessageView(message)
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.error_title)
        .setView(contentView)
        .setNeutralButton(R.string.per_app_proxy_action_copy) { _, _ ->
            copyToClipboard(message)
        }
        .setPositiveButton(android.R.string.ok, null)
}

fun Context.errorDialogBuilder(exception: Throwable): MaterialAlertDialogBuilder {
    return errorDialogBuilder(exception.localizedMessage ?: exception.toString())
}

private fun Context.buildSelectableMessageView(message: String): ScrollView {
    val density = resources.displayMetrics.density
    val padding = (16 * density).toInt()
    val textView =
        TextView(this).apply {
            text = message
            setTextIsSelectable(true)
            setPadding(padding, padding, padding, padding)
        }
    return ScrollView(this).apply {
        addView(textView)
    }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.error_title), text))
    Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
}
