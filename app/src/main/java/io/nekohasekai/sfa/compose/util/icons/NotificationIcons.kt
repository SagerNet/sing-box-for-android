package io.nekohasekai.sfa.compose.util.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.AirlineSeatFlat
import androidx.compose.material.icons.filled.AirlineSeatFlatAngled
import androidx.compose.material.icons.filled.AirlineSeatIndividualSuite
import androidx.compose.material.icons.filled.AirlineSeatLegroomExtra
import androidx.compose.material.icons.filled.AirlineSeatLegroomNormal
import androidx.compose.material.icons.filled.AirlineSeatLegroomReduced
import androidx.compose.material.icons.filled.AirlineSeatReclineExtra
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsOff
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.DoDisturb
import androidx.compose.material.icons.filled.DoDisturbAlt
import androidx.compose.material.icons.filled.DoDisturbOff
import androidx.compose.material.icons.filled.DoDisturbOn
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.DoNotDisturbAlt
import androidx.compose.material.icons.filled.DoNotDisturbOff
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.DriveEta
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.ImagesearchRoller
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mms
import androidx.compose.material.icons.filled.More
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NetworkLocked
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.NoEncryptionGmailerrorred
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PersonalVideo
import androidx.compose.material.icons.filled.PhoneBluetoothSpeaker
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PhoneLocked
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.PhonePaused
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.RunningWithErrors
import androidx.compose.material.icons.filled.SdCardAlert
import androidx.compose.material.icons.filled.SimCardAlert
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SmsFailed
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material.icons.filled.SyncLock
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TapAndPlay
import androidx.compose.material.icons.filled.TimeToLeave
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VideoChat
import androidx.compose.material.icons.filled.VoiceChat
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiCalling
import androidx.compose.material.icons.filled.WifiOff
import io.nekohasekai.sfa.compose.util.ProfileIcon

/**
 * Notification category icons - Alerts and status updates
 * Based on Google's Material Design Icons taxonomy
 */
object NotificationIcons {
    val icons =
        listOf(
            ProfileIcon("account_tree", Icons.Filled.AccountTree, "Account Tree"),
            ProfileIcon("adb", Icons.Filled.Adb, "ADB"),
            ProfileIcon("airline_seat_flat", Icons.Filled.AirlineSeatFlat, "Seat Flat"),
            ProfileIcon("airline_seat_flat_angled", Icons.Filled.AirlineSeatFlatAngled, "Seat Angled"),
            ProfileIcon(
                "airline_seat_individual_suite",
                Icons.Filled.AirlineSeatIndividualSuite,
                "Seat Suite",
            ),
            ProfileIcon(
                "airline_seat_legroom_extra",
                Icons.Filled.AirlineSeatLegroomExtra,
                "Legroom Extra",
            ),
            ProfileIcon(
                "airline_seat_legroom_normal",
                Icons.Filled.AirlineSeatLegroomNormal,
                "Legroom Normal",
            ),
            ProfileIcon(
                "airline_seat_legroom_reduced",
                Icons.Filled.AirlineSeatLegroomReduced,
                "Legroom Reduced",
            ),
            ProfileIcon(
                "airline_seat_recline_extra",
                Icons.Filled.AirlineSeatReclineExtra,
                "Recline Extra",
            ),
            ProfileIcon(
                "airline_seat_recline_normal",
                Icons.Filled.AirlineSeatReclineNormal,
                "Recline Normal",
            ),
            ProfileIcon("bluetooth_audio", Icons.Filled.BluetoothAudio, "Bluetooth Audio"),
            ProfileIcon("confirmation_number", Icons.Filled.ConfirmationNumber, "Confirmation Number"),
            ProfileIcon("directions_off", Icons.Filled.DirectionsOff, "Directions Off"),
            ProfileIcon("disc_full", Icons.Filled.DiscFull, "Disc Full"),
            ProfileIcon("do_disturb", Icons.Filled.DoDisturb, "Do Disturb"),
            ProfileIcon("do_disturb_alt", Icons.Filled.DoDisturbAlt, "Do Disturb Alt"),
            ProfileIcon("do_disturb_off", Icons.Filled.DoDisturbOff, "Do Disturb Off"),
            ProfileIcon("do_disturb_on", Icons.Filled.DoDisturbOn, "Do Disturb On"),
            ProfileIcon("do_not_disturb", Icons.Filled.DoNotDisturb, "Do Not Disturb"),
            ProfileIcon("do_not_disturb_alt", Icons.Filled.DoNotDisturbAlt, "DND Alt"),
            ProfileIcon("do_not_disturb_off", Icons.Filled.DoNotDisturbOff, "DND Off"),
            ProfileIcon("do_not_disturb_on", Icons.Filled.DoNotDisturbOn, "DND On"),
            ProfileIcon("drive_eta", Icons.Filled.DriveEta, "Drive ETA"),
            ProfileIcon("enhanced_encryption", Icons.Filled.EnhancedEncryption, "Enhanced Encryption"),
            ProfileIcon("event_available", Icons.Filled.EventAvailable, "Event Available"),
            ProfileIcon("event_busy", Icons.Filled.EventBusy, "Event Busy"),
            ProfileIcon("event_note", Icons.Filled.EventNote, "Event Note"),
            ProfileIcon("folder_special", Icons.Filled.FolderSpecial, "Folder Special"),
            ProfileIcon("imagesearch_roller", Icons.Filled.ImagesearchRoller, "Image Search Roller"),
            ProfileIcon("live_tv", Icons.Filled.LiveTv, "Live TV"),
            ProfileIcon("mms", Icons.Filled.Mms, "MMS"),
            ProfileIcon("more", Icons.Filled.More, "More"),
            ProfileIcon("network_check", Icons.Filled.NetworkCheck, "Network Check"),
            ProfileIcon("network_locked", Icons.Filled.NetworkLocked, "Network Locked"),
            ProfileIcon("no_encryption", Icons.Filled.NoEncryption, "No Encryption"),
            ProfileIcon(
                "no_encryption_gmailerrorred",
                Icons.Filled.NoEncryptionGmailerrorred,
                "No Encryption Error",
            ),
            ProfileIcon("ondemand_video", Icons.Filled.OndemandVideo, "On Demand Video"),
            ProfileIcon("personal_video", Icons.Filled.PersonalVideo, "Personal Video"),
            ProfileIcon(
                "phone_bluetooth_speaker",
                Icons.Filled.PhoneBluetoothSpeaker,
                "Phone Bluetooth",
            ),
            ProfileIcon("phone_callback", Icons.Filled.PhoneCallback, "Phone Callback"),
            ProfileIcon("phone_forwarded", Icons.Filled.PhoneForwarded, "Phone Forwarded"),
            ProfileIcon("phone_in_talk", Icons.Filled.PhoneInTalk, "Phone In Talk"),
            ProfileIcon("phone_locked", Icons.Filled.PhoneLocked, "Phone Locked"),
            ProfileIcon("phone_missed", Icons.Filled.PhoneMissed, "Phone Missed"),
            ProfileIcon("phone_paused", Icons.Filled.PhonePaused, "Phone Paused"),
            ProfileIcon("power", Icons.Filled.Power, "Power"),
            ProfileIcon("power_off", Icons.Filled.PowerOff, "Power Off"),
            ProfileIcon("priority_high", Icons.Filled.PriorityHigh, "Priority High"),
            ProfileIcon("running_with_errors", Icons.Filled.RunningWithErrors, "Running With Errors"),
            ProfileIcon("sd_card_alert", Icons.Filled.SdCardAlert, "SD Card Alert"),
            ProfileIcon("sim_card_alert", Icons.Filled.SimCardAlert, "SIM Card Alert"),
            ProfileIcon("sms", Icons.Filled.Sms, "SMS"),
            ProfileIcon("sms_failed", Icons.Filled.SmsFailed, "SMS Failed"),
            ProfileIcon("support_agent", Icons.Filled.SupportAgent, "Support Agent"),
            ProfileIcon("sync", Icons.Filled.Sync, "Sync"),
            ProfileIcon("sync_disabled", Icons.Filled.SyncDisabled, "Sync Disabled"),
            ProfileIcon("sync_lock", Icons.Filled.SyncLock, "Sync Lock"),
            ProfileIcon("sync_problem", Icons.Filled.SyncProblem, "Sync Problem"),
            ProfileIcon("system_update", Icons.Filled.SystemUpdate, "System Update"),
            ProfileIcon("tap_and_play", Icons.Filled.TapAndPlay, "Tap and Play"),
            ProfileIcon("time_to_leave", Icons.Filled.TimeToLeave, "Time to Leave"),
            ProfileIcon("tv_off", Icons.Filled.TvOff, "TV Off"),
            ProfileIcon("vibration", Icons.Filled.Vibration, "Vibration"),
            ProfileIcon("video_chat", Icons.Filled.VideoChat, "Video Chat"),
            ProfileIcon("voice_chat", Icons.Filled.VoiceChat, "Voice Chat"),
            ProfileIcon("vpn_lock", Icons.Filled.VpnLock, "VPN Lock"),
            ProfileIcon("wc", Icons.Filled.Wc, "WC"),
            ProfileIcon("wifi", Icons.Filled.Wifi, "WiFi"),
            ProfileIcon("wifi_calling", Icons.Filled.WifiCalling, "WiFi Calling"),
            ProfileIcon("wifi_off", Icons.Filled.WifiOff, "WiFi Off"),
        )
}
