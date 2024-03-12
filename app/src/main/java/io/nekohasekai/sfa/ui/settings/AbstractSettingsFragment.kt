package io.nekohasekai.sfa.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.XmlRes
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sfa.R

open class AbstractSettingsFragment(@XmlRes val resId: Int) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(resId, rootKey)
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ): RecyclerView {
        val recyclerView = inflater
            .inflate(R.layout.view_prefenence_screen, parent, false) as RecyclerView
        recyclerView.layoutManager = onCreateLayoutManager()
        return recyclerView
    }

}