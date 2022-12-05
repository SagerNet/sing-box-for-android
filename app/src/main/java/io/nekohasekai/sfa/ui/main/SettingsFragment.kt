package io.nekohasekai.sfa.ui.main

import android.os.Bundle
import io.nekohasekai.preference.PreferenceFragmentWrapper
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings

class SettingsFragment : PreferenceFragmentWrapper() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = Settings.dataStore
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}