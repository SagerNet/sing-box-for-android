package io.nekohasekai.sfa.compose.util.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.automirrored.filled.FeaturedVideo
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArtTrack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CallToAction
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FiberDvr
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.FiberPin
import androidx.compose.material.icons.filled.FiberSmartRecord
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.InterpreterMode
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicExternalOff
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MissedVideoCall
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.MusicVideo
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PausePresentation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PlayDisabled
import androidx.compose.material.icons.filled.PlayLesson
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.PlaylistAddCheckCircle
import androidx.compose.material.icons.filled.PlaylistAddCircle
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RecentActors
import androidx.compose.material.icons.filled.RemoveFromQueue
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.ReplayCircleFilled
import androidx.compose.material.icons.filled.Sd
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLabel
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.VideoStable
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.VideogameAssetOff
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.WebAsset
import androidx.compose.material.icons.filled.WebAssetOff
import io.nekohasekai.sfa.compose.util.ProfileIcon

/**
 * AV (Audio/Video) category icons - Media controls and playback
 * Based on Google's Material Design Icons taxonomy
 */
object AVIcons {
    val icons =
        listOf(
            // ProfileIcon("10k", Icons.Filled.TenK, "10K"), // Not available in compose-material-icons-extended
            // ProfileIcon("10mp", Icons.Filled.TenMp, "10MP"),
            // ProfileIcon("11mp", Icons.Filled.ElevenMp, "11MP"),
            // ProfileIcon("12mp", Icons.Filled.TwelveMp, "12MP"),
            // ProfileIcon("13mp", Icons.Filled.ThirteenMp, "13MP"),
            // ProfileIcon("14mp", Icons.Filled.FourteenMp, "14MP"),
            // ProfileIcon("15mp", Icons.Filled.FifteenMp, "15MP"),
            // ProfileIcon("16mp", Icons.Filled.SixteenMp, "16MP"),
            // ProfileIcon("17mp", Icons.Filled.SeventeenMp, "17MP"),
            // ProfileIcon("18mp", Icons.Filled.EighteenMp, "18MP"),
            // ProfileIcon("19mp", Icons.Filled.NineteenMp, "19MP"),
            // ProfileIcon("1k", Icons.Filled.OneK, "1K"),
            // ProfileIcon("1k_plus", Icons.Filled.OneKPlus, "1K+"),
            // ProfileIcon("20mp", Icons.Filled.TwentyMp, "20MP"),
            // ProfileIcon("21mp", Icons.Filled.TwentyOneMp, "21MP"),
            // ProfileIcon("22mp", Icons.Filled.TwentyTwoMp, "22MP"),
            // ProfileIcon("23mp", Icons.Filled.TwentyThreeMp, "23MP"),
            // ProfileIcon("24mp", Icons.Filled.TwentyFourMp, "24MP"),
            // ProfileIcon("2k", Icons.Filled.TwoK, "2K"),
            // ProfileIcon("2k_plus", Icons.Filled.TwoKPlus, "2K+"),
            // ProfileIcon("2mp", Icons.Filled.TwoMp, "2MP"),
            // ProfileIcon("3k", Icons.Filled.ThreeK, "3K"),
            // ProfileIcon("3k_plus", Icons.Filled.ThreeKPlus, "3K+"),
            // ProfileIcon("3mp", Icons.Filled.ThreeMp, "3MP"),
            // ProfileIcon("4k", Icons.Filled.FourK, "4K"), // Not available
            // ProfileIcon("4k_plus", Icons.Filled.FourKPlus, "4K+"), // Not available
            // ProfileIcon("4mp", Icons.Filled.FourMp, "4MP"),
            // ProfileIcon("5g", Icons.Filled.FiveG, "5G"),
            // ProfileIcon("5k", Icons.Filled.FiveK, "5K"),
            // ProfileIcon("5k_plus", Icons.Filled.FiveKPlus, "5K+"),
            // ProfileIcon("5mp", Icons.Filled.FiveMp, "5MP"),
            // ProfileIcon("6k", Icons.Filled.SixK, "6K"),
            // ProfileIcon("6k_plus", Icons.Filled.SixKPlus, "6K+"),
            // ProfileIcon("6mp", Icons.Filled.SixMp, "6MP"),
            // ProfileIcon("7k", Icons.Filled.SevenK, "7K"),
            // ProfileIcon("7k_plus", Icons.Filled.SevenKPlus, "7K+"),
            // ProfileIcon("7mp", Icons.Filled.SevenMp, "7MP"),
            // ProfileIcon("8k", Icons.Filled.EightK, "8K"),
            // ProfileIcon("8k_plus", Icons.Filled.EightKPlus, "8K+"),
            // ProfileIcon("8mp", Icons.Filled.EightMp, "8MP"),
            // ProfileIcon("9k", Icons.Filled.NineK, "9K"),
            // ProfileIcon("9k_plus", Icons.Filled.NineKPlus, "9K+"),
            // ProfileIcon("9mp", Icons.Filled.NineMp, "9MP"),
            ProfileIcon("add_to_queue", Icons.Filled.AddToQueue, "Add to Queue"),
            ProfileIcon("airplay", Icons.Filled.Airplay, "Airplay"),
            ProfileIcon("album", Icons.Filled.Album, "Album"),
            ProfileIcon("art_track", Icons.Filled.ArtTrack, "Art Track"),
            ProfileIcon("audio_file", Icons.Filled.AudioFile, "Audio File"),
            ProfileIcon("av_timer", Icons.Filled.AvTimer, "AV Timer"),
            ProfileIcon("branding_watermark", Icons.Filled.BrandingWatermark, "Watermark"),
            ProfileIcon("call_to_action", Icons.Filled.CallToAction, "Call to Action"),
            ProfileIcon("closed_caption", Icons.Filled.ClosedCaption, "Closed Caption"),
            ProfileIcon("closed_caption_disabled", Icons.Filled.ClosedCaptionDisabled, "CC Disabled"),
            ProfileIcon("closed_caption_off", Icons.Filled.ClosedCaptionOff, "CC Off"),
            ProfileIcon("control_camera", Icons.Filled.ControlCamera, "Control Camera"),
            ProfileIcon("equalizer", Icons.Filled.Equalizer, "Equalizer"),
            ProfileIcon("explicit", Icons.Filled.Explicit, "Explicit"),
            ProfileIcon("fast_forward", Icons.Filled.FastForward, "Fast Forward"),
            ProfileIcon("fast_rewind", Icons.Filled.FastRewind, "Fast Rewind"),
            ProfileIcon(
                "featured_play_list",
                Icons.AutoMirrored.Filled.FeaturedPlayList,
                "Featured Playlist",
            ),
            ProfileIcon("featured_video", Icons.AutoMirrored.Filled.FeaturedVideo, "Featured Video"),
            ProfileIcon("fiber_dvr", Icons.Filled.FiberDvr, "DVR"),
            ProfileIcon("fiber_manual_record", Icons.Filled.FiberManualRecord, "Record"),
            ProfileIcon("fiber_new", Icons.Filled.FiberNew, "New"),
            ProfileIcon("fiber_pin", Icons.Filled.FiberPin, "Pin"),
            ProfileIcon("fiber_smart_record", Icons.Filled.FiberSmartRecord, "Smart Record"),
            ProfileIcon("forward_10", Icons.Filled.Forward10, "Forward 10"),
            ProfileIcon("forward_30", Icons.Filled.Forward30, "Forward 30"),
            ProfileIcon("forward_5", Icons.Filled.Forward5, "Forward 5"),
            ProfileIcon("games", Icons.Filled.Games, "Games"),
            ProfileIcon("hd", Icons.Filled.Hd, "HD"),
            ProfileIcon("hearing", Icons.Filled.Hearing, "Hearing"),
            ProfileIcon("hearing_disabled", Icons.Filled.HearingDisabled, "Hearing Disabled"),
            ProfileIcon("high_quality", Icons.Filled.HighQuality, "High Quality"),
            ProfileIcon("interpreter_mode", Icons.Filled.InterpreterMode, "Interpreter Mode"),
            ProfileIcon("library_add", Icons.Filled.LibraryAdd, "Library Add"),
            ProfileIcon("library_add_check", Icons.Filled.LibraryAddCheck, "Library Check"),
            ProfileIcon("library_books", Icons.Filled.LibraryBooks, "Library Books"),
            ProfileIcon("library_music", Icons.Filled.LibraryMusic, "Library Music"),
            ProfileIcon("loop", Icons.Filled.Loop, "Loop"),
            ProfileIcon("lyrics", Icons.Filled.Lyrics, "Lyrics"),
            ProfileIcon("mic", Icons.Filled.Mic, "Mic"),
            ProfileIcon("mic_external_off", Icons.Filled.MicExternalOff, "Mic External Off"),
            ProfileIcon("mic_external_on", Icons.Filled.MicExternalOn, "Mic External On"),
            ProfileIcon("mic_none", Icons.Filled.MicNone, "Mic None"),
            ProfileIcon("mic_off", Icons.Filled.MicOff, "Mic Off"),
            ProfileIcon("missed_video_call", Icons.Filled.MissedVideoCall, "Missed Video Call"),
            ProfileIcon("movie", Icons.Filled.Movie, "Movie"),
            ProfileIcon("movie_creation", Icons.Filled.MovieCreation, "Movie Creation"),
            ProfileIcon("movie_filter", Icons.Filled.MovieFilter, "Movie Filter"),
            ProfileIcon("music_note", Icons.Filled.MusicNote, "Music Note"),
            ProfileIcon("music_off", Icons.Filled.MusicOff, "Music Off"),
            ProfileIcon("music_video", Icons.Filled.MusicVideo, "Music Video"),
            ProfileIcon("new_releases", Icons.Filled.NewReleases, "New Releases"),
            ProfileIcon("not_interested", Icons.Filled.NotInterested, "Not Interested"),
            ProfileIcon("note", Icons.AutoMirrored.Filled.Note, "Note"),
            ProfileIcon("pause", Icons.Filled.Pause, "Pause"),
            ProfileIcon("pause_circle", Icons.Filled.PauseCircle, "Pause Circle"),
            ProfileIcon("pause_circle_filled", Icons.Filled.PauseCircleFilled, "Pause Filled"),
            ProfileIcon("pause_circle_outline", Icons.Filled.PauseCircleOutline, "Pause Outline"),
            ProfileIcon("pause_presentation", Icons.Filled.PausePresentation, "Pause Presentation"),
            ProfileIcon("play_arrow", Icons.Filled.PlayArrow, "Play"),
            ProfileIcon("play_circle", Icons.Filled.PlayCircle, "Play Circle"),
            ProfileIcon("play_circle_filled", Icons.Filled.PlayCircleFilled, "Play Filled"),
            ProfileIcon("play_circle_outline", Icons.Filled.PlayCircleOutline, "Play Outline"),
            ProfileIcon("play_disabled", Icons.Filled.PlayDisabled, "Play Disabled"),
            ProfileIcon("play_lesson", Icons.Filled.PlayLesson, "Play Lesson"),
            ProfileIcon("playlist_add", Icons.Filled.PlaylistAdd, "Playlist Add"),
            ProfileIcon("playlist_add_check", Icons.Filled.PlaylistAddCheck, "Playlist Check"),
            ProfileIcon(
                "playlist_add_check_circle",
                Icons.Filled.PlaylistAddCheckCircle,
                "Playlist Circle",
            ),
            ProfileIcon("playlist_add_circle", Icons.Filled.PlaylistAddCircle, "Add Circle"),
            ProfileIcon("playlist_play", Icons.Filled.PlaylistPlay, "Playlist Play"),
            ProfileIcon("playlist_remove", Icons.Filled.PlaylistRemove, "Playlist Remove"),
            ProfileIcon("queue", Icons.Filled.Queue, "Queue"),
            ProfileIcon("queue_music", Icons.AutoMirrored.Filled.QueueMusic, "Queue Music"),
            ProfileIcon("queue_play_next", Icons.Filled.QueuePlayNext, "Play Next"),
            ProfileIcon("radio", Icons.Filled.Radio, "Radio"),
            ProfileIcon("recent_actors", Icons.Filled.RecentActors, "Recent Actors"),
            ProfileIcon("remove_from_queue", Icons.Filled.RemoveFromQueue, "Remove Queue"),
            ProfileIcon("repeat", Icons.Filled.Repeat, "Repeat"),
            ProfileIcon("repeat_on", Icons.Filled.RepeatOn, "Repeat On"),
            ProfileIcon("repeat_one", Icons.Filled.RepeatOne, "Repeat One"),
            ProfileIcon("repeat_one_on", Icons.Filled.RepeatOneOn, "Repeat One On"),
            ProfileIcon("replay", Icons.Filled.Replay, "Replay"),
            ProfileIcon("replay_10", Icons.Filled.Replay10, "Replay 10"),
            ProfileIcon("replay_30", Icons.Filled.Replay30, "Replay 30"),
            ProfileIcon("replay_5", Icons.Filled.Replay5, "Replay 5"),
            ProfileIcon("replay_circle_filled", Icons.Filled.ReplayCircleFilled, "Replay Circle"),
            ProfileIcon("sd", Icons.Filled.Sd, "SD"),
            ProfileIcon("sd_card", Icons.Filled.SdCard, "SD Card"),
            ProfileIcon("shuffle", Icons.Filled.Shuffle, "Shuffle"),
            ProfileIcon("shuffle_on", Icons.Filled.ShuffleOn, "Shuffle On"),
            ProfileIcon("skip_next", Icons.Filled.SkipNext, "Skip Next"),
            ProfileIcon("skip_previous", Icons.Filled.SkipPrevious, "Skip Previous"),
            ProfileIcon("slow_motion_video", Icons.Filled.SlowMotionVideo, "Slow Motion"),
            ProfileIcon("snooze", Icons.Filled.Snooze, "Snooze"),
            ProfileIcon("sort_by_alpha", Icons.Filled.SortByAlpha, "Sort Alpha"),
            ProfileIcon("speed", Icons.Filled.Speed, "Speed"),
            ProfileIcon("stop", Icons.Filled.Stop, "Stop"),
            ProfileIcon("stop_circle", Icons.Filled.StopCircle, "Stop Circle"),
            ProfileIcon("stop_screen_share", Icons.Filled.StopScreenShare, "Stop Share"),
            ProfileIcon("subscriptions", Icons.Filled.Subscriptions, "Subscriptions"),
            ProfileIcon("subtitles", Icons.Filled.Subtitles, "Subtitles"),
            ProfileIcon("surround_sound", Icons.Filled.SurroundSound, "Surround Sound"),
            ProfileIcon("video_call", Icons.Filled.VideoCall, "Video Call"),
            ProfileIcon("video_camera_back", Icons.Filled.VideoCameraBack, "Camera Back"),
            ProfileIcon("video_camera_front", Icons.Filled.VideoCameraFront, "Camera Front"),
            // ProfileIcon("video_collection", Icons.Filled.VideoCollection, "Video Collection"),
            ProfileIcon("video_file", Icons.Filled.VideoFile, "Video File"),
            ProfileIcon("video_label", Icons.Filled.VideoLabel, "Video Label"),
            ProfileIcon("video_library", Icons.Filled.VideoLibrary, "Video Library"),
            ProfileIcon("video_settings", Icons.Filled.VideoSettings, "Video Settings"),
            ProfileIcon("video_stable", Icons.Filled.VideoStable, "Video Stable"),
            ProfileIcon("videocam", Icons.Filled.Videocam, "Videocam"),
            ProfileIcon("videocam_off", Icons.Filled.VideocamOff, "Videocam Off"),
            ProfileIcon("videogame_asset", Icons.Filled.VideogameAsset, "Videogame"),
            ProfileIcon("videogame_asset_off", Icons.Filled.VideogameAssetOff, "Videogame Off"),
            ProfileIcon("volume_down", Icons.AutoMirrored.Filled.VolumeDown, "Volume Down"),
            ProfileIcon("volume_mute", Icons.AutoMirrored.Filled.VolumeMute, "Mute"),
            ProfileIcon("volume_off", Icons.AutoMirrored.Filled.VolumeOff, "Volume Off"),
            ProfileIcon("volume_up", Icons.AutoMirrored.Filled.VolumeUp, "Volume Up"),
            ProfileIcon("web", Icons.Filled.Web, "Web"),
            ProfileIcon("web_asset", Icons.Filled.WebAsset, "Web Asset"),
            ProfileIcon("web_asset_off", Icons.Filled.WebAssetOff, "Web Asset Off"),
        )
}
