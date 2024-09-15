package io.nekohasekai.sfa.ktx

import android.content.Context
import android.content.DialogInterface
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sfa.R

fun Context.errorDialogBuilder(@StringRes messageId: Int): MaterialAlertDialogBuilder {
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.error_title)
        .setMessage(messageId)
        .setPositiveButton(android.R.string.ok, null)
}

fun Context.errorDialogBuilder(message: String): MaterialAlertDialogBuilder {
    return errorDialogBuilder(message, null)
}

fun Context.errorDialogBuilder(
    message: String,
    listener: DialogInterface.OnClickListener?
): MaterialAlertDialogBuilder {
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.error_title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, listener)
}

fun Context.errorDialogBuilder(exception: Throwable): MaterialAlertDialogBuilder {
    return errorDialogBuilder(exception, null)
}

fun Context.errorDialogBuilder(
    exception: Throwable,
    listener: DialogInterface.OnClickListener?
): MaterialAlertDialogBuilder {
    return errorDialogBuilder(exception.localizedMessage ?: exception.toString(), listener)
}