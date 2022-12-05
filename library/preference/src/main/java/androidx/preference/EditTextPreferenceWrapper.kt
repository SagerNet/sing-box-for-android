package androidx.preference

object EditTextPreferenceWrapper {

    @JvmStatic
    fun getOnBindEditTextListener(preference: EditTextPreference) =
        preference.onBindEditTextListener

}