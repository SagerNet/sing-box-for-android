package io.nekohasekai.sfa.constant

object SettingsKey {
    const val SELECTED_PROFILE = "selected_profile"
    const val SERVICE_MODE = "service_mode"
    const val CHECK_UPDATE_ENABLED = "check_update_enabled"
    const val UPDATE_CHECK_PROMPTED = "update_check_prompted"
    const val UPDATE_SOURCE = "update_source"
    const val UPDATE_TRACK = "update_track"
    const val FDROID_MIRROR_URL = "fdroid_mirror_url"
    const val FDROID_CUSTOM_MIRRORS = "fdroid_custom_mirrors"
    const val SILENT_INSTALL_ENABLED = "silent_install_enabled"
    const val SILENT_INSTALL_METHOD = "silent_install_method"
    const val AUTO_UPDATE_ENABLED = "auto_update_enabled"
    const val DYNAMIC_NOTIFICATION = "dynamic_notification"
    const val DISABLE_DEPRECATED_WARNINGS = "disable_deprecated_warnings"

    const val AUTO_REDIRECT = "auto_redirect"
    const val PER_APP_PROXY_ENABLED = "per_app_proxy_enabled"
    const val PER_APP_PROXY_MODE = "per_app_proxy_mode"
    const val PER_APP_PROXY_LIST = "per_app_proxy_list"
    const val PER_APP_PROXY_MANAGED_MODE = "per_app_proxy_managed_mode"
    const val PER_APP_PROXY_MANAGED_LIST = "per_app_proxy_managed_list"
    const val PER_APP_PROXY_PACKAGE_QUERY_MODE = "per_app_proxy_package_query_mode"

    const val ALLOW_BYPASS = "allow_bypass"
    const val SYSTEM_PROXY_ENABLED = "system_proxy_enabled"

    const val PRIVILEGE_SETTINGS_ENABLED = "hide_settings_enabled"
    const val PRIVILEGE_SETTINGS_LIST = "hide_settings_list"
    const val PRIVILEGE_SETTINGS_INTERFACE_RENAME_ENABLED = "hide_settings_interface_rename_enabled"
    const val PRIVILEGE_SETTINGS_INTERFACE_PREFIX = "hide_settings_interface_prefix"

    // OOM killer
    const val OOM_KILLER_ENABLED = "oom_killer_enabled"
    const val OOM_KILLER_DISABLED = "oom_killer_disabled"
    const val OOM_MEMORY_LIMIT_MB = "oom_memory_limit_mb"

    // dashboard
    const val DASHBOARD_ITEM_ORDER = "dashboard_item_order"
    const val DASHBOARD_DISABLED_ITEMS = "dashboard_disabled_items"

    // Tailscale SSH
    const val TAILSCALE_SSH_REMEMBERED_USERNAMES = "tailscale_ssh_remembered_usernames"
    const val TAILSCALE_SSH_QUICK_CONNECT_PEERS = "tailscale_ssh_quick_connect_peers"
    const val TAILSCALE_SSH_LIGHT_THEME = "tailscale_ssh_light_theme"
    const val TAILSCALE_SSH_DARK_THEME = "tailscale_ssh_dark_theme"
    const val TAILSCALE_SSH_FONT_FAMILY = "tailscale_ssh_font_family"
    const val TAILSCALE_SSH_FONT_SIZE = "tailscale_ssh_font_size"
    const val TAILSCALE_SSH_CUSTOM_FONT_PATH = "tailscale_ssh_custom_font_path"

    // cache
    const val STARTED_BY_USER = "started_by_user"
    const val CACHED_UPDATE_INFO = "cached_update_info"
    const val CACHED_APK_PATH = "cached_apk_path"
    const val LAST_SHOWN_UPDATE_VERSION = "last_shown_update_version"
}
