package io.nekohasekai.sfa.ktx

import androidx.annotation.ArrayRes
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sfa.R

var TextInputLayout.text: String
    get() = editText?.text?.toString() ?: ""
    set(value) {
        editText?.setText(value)
    }

var TextInputLayout.error: String
    get() = editText?.error?.toString() ?: ""
    set(value) {
        editText?.error = value
    }


fun TextInputLayout.setSimpleItems(@ArrayRes redId: Int) {
    (editText as? MaterialAutoCompleteTextView)?.setSimpleItems(redId)
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