package io.nekohasekai.sfa.ktx

import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sfa.R

var TextInputLayout.text: String
    get() = editText?.text?.toString() ?: ""
    set(value) {
        editText?.setText(value)
    }

fun TextInputLayout.removeErrorIfNotEmpty() {
    addOnEditTextAttachedListener {
        editText?.addTextChangedListener {
            if (text.isNotBlank()) {
                error = null
            }
        }
    }
}

fun TextInputLayout.showErrorIfEmpty(): Boolean {
    if (text.isBlank()) {
        error = context.getString(R.string.profile_input_required)
        return true
    }
    return false
}


fun TextInputLayout.addTextChangedListener(listener: (String) -> Unit) {
    addOnEditTextAttachedListener {
        editText?.addTextChangedListener {
            listener(it?.toString() ?: "")
        }
    }
}