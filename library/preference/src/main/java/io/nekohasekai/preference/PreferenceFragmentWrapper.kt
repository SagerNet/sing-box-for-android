package io.nekohasekai.preference

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

abstract class PreferenceFragmentWrapper : PreferenceFragmentCompat() {

    companion object {
        private const val DIALOG_FRAGMENT_TAG =
            "io.nekohasekai.sfa.preference.PreferenceFragmentWrapper.DIALOG"
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        var handled = false
        //  If the callback fragment doesn't handle OnPreferenceDisplayDialogCallback, looks up
        //  its parent fragment in the hierarchy that implements the callback until the first
        //  one that returns true
        //  If the callback fragment doesn't handle OnPreferenceDisplayDialogCallback, looks up
        //  its parent fragment in the hierarchy that implements the callback until the first
        //  one that returns true
        var callbackFragment: Fragment? = this
        while (!handled && callbackFragment != null) {
            if (callbackFragment is OnPreferenceDisplayDialogCallback) {
                handled = (callbackFragment as OnPreferenceDisplayDialogCallback)
                    .onPreferenceDisplayDialog(this, preference)
            }
            callbackFragment = callbackFragment.parentFragment
        }
        if (!handled && context is OnPreferenceDisplayDialogCallback) {
            handled = (context as OnPreferenceDisplayDialogCallback).onPreferenceDisplayDialog(
                this,
                preference
            )
        }
        // Check the Activity as well in case getContext was overridden to return something other
        // than the Activity.
        // Check the Activity as well in case getContext was overridden to return something other
        // than the Activity.
        if (!handled && activity is OnPreferenceDisplayDialogCallback) {
            handled = (activity as OnPreferenceDisplayDialogCallback).onPreferenceDisplayDialog(
                this,
                preference
            )
        }

        if (handled) {
            return
        }

        // check if dialog is already showing
        if (parentFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return
        }

        val f: DialogFragment
        f = if (preference is EditTextPreference) {
            EditTextPreferenceDialogFragmentCompat.newInstance(preference.getKey())
        } else if (preference is ListPreference) {
            ListPreferenceDialogFragmentCompat.newInstance(preference.getKey())
        } else if (preference is MultiSelectListPreference) {
            MultiSelectListPreferenceDialogFragmentCompat.newInstance(preference.getKey())
        } else {
            throw IllegalArgumentException(
                "Cannot display dialog for an unknown Preference type: "
                        + preference.javaClass.simpleName
                        + ". Make sure to implement onPreferenceDisplayDialog() to handle "
                        + "displaying a custom dialog for this Preference."
            )
        }
        f.setTargetFragment(this, 0)
        f.show(parentFragmentManager, DIALOG_FRAGMENT_TAG)
    }

}