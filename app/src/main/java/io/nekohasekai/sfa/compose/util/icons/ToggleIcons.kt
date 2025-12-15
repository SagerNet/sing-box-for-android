package io.nekohasekai.sfa.compose.util.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarBorderPurple500
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.StarPurple500
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import io.nekohasekai.sfa.compose.util.ProfileIcon

/**
 * Toggle category icons - Switches and toggles
 * Based on Google's Material Design Icons taxonomy
 */
object ToggleIcons {
    val icons =
        listOf(
            ProfileIcon("check_box", Icons.Filled.CheckBox, "Check Box"),
            ProfileIcon(
                "check_box_outline_blank",
                Icons.Filled.CheckBoxOutlineBlank,
                "Check Box Blank",
            ),
            ProfileIcon("indeterminate_check_box", Icons.Filled.IndeterminateCheckBox, "Indeterminate"),
            ProfileIcon("radio_button_checked", Icons.Filled.RadioButtonChecked, "Radio Checked"),
            ProfileIcon("radio_button_unchecked", Icons.Filled.RadioButtonUnchecked, "Radio Unchecked"),
            ProfileIcon("star", Icons.Filled.Star, "Star"),
            ProfileIcon("star_border", Icons.Filled.StarBorder, "Star Border"),
            ProfileIcon("star_border_purple500", Icons.Filled.StarBorderPurple500, "Star Purple"),
            ProfileIcon("star_half", Icons.Filled.StarHalf, "Star Half"),
            ProfileIcon("star_outline", Icons.Filled.StarOutline, "Star Outline"),
            ProfileIcon("star_purple500", Icons.Filled.StarPurple500, "Star Purple"),
            ProfileIcon("toggle_off", Icons.Filled.ToggleOff, "Toggle Off"),
            ProfileIcon("toggle_on", Icons.Filled.ToggleOn, "Toggle On"),
        )
}
