/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.metrolist.music.R

/**
 * One searchable setting: exact title resource, navigation route, and optional synonyms/keywords.
 * [id] doubles as the highlight key on [com.metrolist.music.ui.component.Material3SettingsItem].
 */
data class SettingsSearchEntry(
    val id: String,
    @StringRes val titleRes: Int,
    val route: String,
    val synonyms: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
)

data class SettingsSearchSuggestion(
    val entry: SettingsSearchEntry,
    val title: String,
    val sectionLabel: String?,
)

/** Synonym groups: a query token matches if any group-mate appears in the setting corpus. */
private val synonymGroups: List<Set<String>> =
    listOf(
        setOf("dark", "night", "black", "amoled", "oled", "pure"),
        setOf("theme", "appearance", "look", "color", "colour", "style"),
        setOf("cache", "storage", "disk", "space", "memory"),
        setOf("lyrics", "lyric", "words", "karaoke", "synced"),
        setOf("audio", "sound", "quality", "bitrate", "hires", "hi-res"),
        setOf("history", "recent", "past", "listen"),
        setOf("privacy", "secure", "security", "screenshot", "private"),
        setOf("proxy", "network", "vpn", "tor"),
        setOf("discord", "rpc", "presence", "rich"),
        setOf("spotify", "exportify", "taste"),
        setOf("dj", "nano", "host", "radio"),
        setOf("android", "auto", "car", "driving", "vehicle"),
        setOf("playlist", "queue", "list", "mix"),
        setOf("normalize", "normalization", "loudness", "volume", "gain"),
        setOf("silence", "skip", "gap", "quiet"),
        setOf("language", "locale", "country", "region", "content"),
        setOf("explicit", "nsfw", "filter", "hide"),
        setOf("backup", "restore", "export", "import", "csv"),
        setOf("notification", "update", "updater", "version"),
        setOf("scrobble", "scrobbling", "lastfm", "last.fm"),
        setOf("cast", "chromecast", "google"),
        setOf("crossfade", "fade", "gapless"),
        setOf("shuffle", "random", "mix"),
        setOf("download", "offline", "downloaded"),
        setOf("romanize", "romanization", "transliterate", "romaji"),
        setOf("translate", "translation", "deepl", "ai"),
        setOf("player", "playback", "playing"),
        setOf("account", "login", "token", "sign"),
        setOf("section", "browse", "visible", "order", "reorder"),
    )

private fun expandToken(token: String): Set<String> {
    val t = token.lowercase()
    if (t.isBlank()) return emptySet()
    val group =
        synonymGroups.firstOrNull { group ->
            group.any { syn -> syn == t || syn.startsWith(t) || t.startsWith(syn) }
        }
    return (group ?: setOf(t)) + t
}

private fun tokenize(query: String): List<String> =
    query
        .lowercase()
        .split(Regex("[\\s,_/\\-]+"))
        .map { it.trim() }
        .filter { it.length >= 1 }

private fun corpusFor(
    title: String,
    synonyms: List<String>,
    keywords: List<String>,
): List<String> {
    val parts = mutableListOf<String>()
    parts += title.lowercase()
    parts +=
        title
            .lowercase()
            .split(Regex("[\\s,_/\\-]+"))
            .filter { it.isNotBlank() }
    synonyms.forEach { parts += it.lowercase() }
    keywords.forEach { parts += it.lowercase() }
    return parts
}

fun settingsEntryMatches(
    query: String,
    title: String,
    synonyms: List<String> = emptyList(),
    keywords: List<String> = emptyList(),
): Boolean {
    val tokens = tokenize(query)
    if (tokens.isEmpty()) return false
    val corpus = corpusFor(title, synonyms, keywords)
    val corpusText = corpus.joinToString(" ")
    return tokens.all { token ->
        val expanded = expandToken(token)
        expanded.any { candidate ->
            corpus.any { term ->
                term.contains(candidate) || (candidate.length >= 3 && candidate.contains(term))
            } || corpusText.contains(candidate)
        }
    }
}

/**
 * Static index of settings (exact title string resources) with synonyms for search.
 * Keep [id] aligned with `searchKey` on the corresponding settings row.
 */
object SettingsSearchCatalog {
    val entries: List<SettingsSearchEntry> =
        listOf(
            // Hub / sections
            entry("appearance", R.string.appearance, "settings/appearance", listOf("theme", "look", "ui", "display")),
            entry("content", R.string.content, "settings/content", listOf("language", "country", "region", "feed")),
            entry("ai_lyrics_translation", R.string.ai_lyrics_translation, "settings/ai", listOf("translate", "deepl", "ai", "lyrics")),
            entry("dj_settings_title", R.string.dj_settings_title, "settings/dj", listOf("dj", "nano", "gemini", "host")),
            entry("android_auto", R.string.android_auto, "settings/android_auto", listOf("car", "driving", "auto", "vehicle")),
            entry("player_and_audio", R.string.player_and_audio, "settings/player", listOf("playback", "sound", "audio")),
            entry("stream_sources", R.string.stream_sources, "settings/stream_sources", listOf("source", "stream", "cdn")),
            entry("privacy", R.string.privacy, "settings/privacy", listOf("history", "secure", "screenshot")),
            entry("storage", R.string.storage, "settings/storage", listOf("cache", "disk", "space")),
            entry("backup_restore", R.string.backup_restore, "settings/backup_restore", listOf("backup", "restore", "export", "import", "csv")),
            entry("integrations", R.string.integrations, "settings/integrations", listOf("discord", "spotify", "lastfm", "connect")),
            entry("updater", R.string.updater, "settings/updater", listOf("update", "version", "notification")),
            // Appearance
            entry("enable_high_refresh_rate", R.string.enable_high_refresh_rate, "settings/appearance", listOf("120hz", "refresh", "fps", "smooth")),
            entry("enable_landscape_scaling", R.string.enable_landscape_scaling, "settings/appearance", listOf("landscape", "tablet", "scale")),
            entry("enable_dynamic_theme", R.string.enable_dynamic_theme, "settings/appearance", listOf("material you", "wallpaper", "dynamic", "monet")),
            entry("enable_dynamic_icon", R.string.enable_dynamic_icon, "settings/appearance", listOf("icon", "launcher", "dynamic")),
            entry("theme", R.string.theme, "settings/appearance/theme", listOf("dark", "light", "black", "mode", "night")),
            entry("new_mini_player_design", R.string.new_mini_player_design, "settings/appearance", listOf("mini", "bar", "now playing")),
            entry("new_player_design", R.string.new_player_design, "settings/appearance", listOf("player", "design", "layout")),
            entry("player_background_style", R.string.player_background_style, "settings/appearance", listOf("blur", "gradient", "background")),
            entry("hide_player_thumbnail", R.string.hide_player_thumbnail, "settings/appearance", listOf("cover", "art", "thumbnail")),
            entry("crop_album_art", R.string.crop_album_art, "settings/appearance", listOf("crop", "album", "art")),
            entry("player_buttons_style", R.string.player_buttons_style, "settings/appearance", listOf("buttons", "controls", "color")),
            entry("player_slider_style", R.string.player_slider_style, "settings/appearance", listOf("slider", "seek", "wavy", "progress")),
            entry("enable_swipe_thumbnail", R.string.enable_swipe_thumbnail, "settings/appearance", listOf("swipe", "gesture", "next")),
            entry("lyrics_glow_effect", R.string.lyrics_glow_effect, "settings/appearance", listOf("glow", "lyrics", "effect")),
            entry("lyrics_animation_style_title", R.string.lyrics_animation_style_title, "settings/appearance", listOf("karaoke", "animation", "lyrics")),
            entry("lyrics_text_size", R.string.lyrics_text_size, "settings/appearance", listOf("font", "size", "lyrics")),
            entry("lyrics_line_spacing", R.string.lyrics_line_spacing, "settings/appearance", listOf("spacing", "lyrics")),
            entry("lyrics_text_position", R.string.lyrics_text_position, "settings/appearance", listOf("position", "align", "lyrics")),
            entry("lyrics_click_change", R.string.lyrics_click_change, "settings/appearance", listOf("tap", "seek", "lyrics")),
            entry("lyrics_auto_scroll", R.string.lyrics_auto_scroll, "settings/appearance", listOf("scroll", "follow", "lyrics")),
            entry("default_open_tab", R.string.default_open_tab, "settings/appearance", listOf("home", "start", "tab", "launch")),
            entry("default_lib_chips", R.string.default_lib_chips, "settings/appearance", listOf("library", "filter", "chips")),
            entry("slim_navbar", R.string.slim_navbar, "settings/appearance", listOf("nav", "bar", "slim", "bottom")),
            entry("grid_cell_size", R.string.grid_cell_size, "settings/appearance", listOf("grid", "size", "tiles")),
            entry("display_density", R.string.display_density, "settings/appearance", listOf("density", "dpi", "compact")),
            // Content
            entry("content_language", R.string.content_language, "settings/content", listOf("language", "locale")),
            entry("content_country", R.string.content_country, "settings/content", listOf("country", "region", "location")),
            entry("hide_explicit", R.string.hide_explicit, "settings/content", listOf("explicit", "nsfw", "filter", "mature")),
            entry("hide_video_songs", R.string.hide_video_songs, "settings/content", listOf("video", "mv", "music video")),
            entry("hide_youtube_shorts", R.string.hide_youtube_shorts, "settings/content", listOf("shorts", "short", "reels")),
            entry("app_language", R.string.app_language, "settings/content", listOf("language", "locale", "ui")),
            entry("enable_proxy", R.string.enable_proxy, "settings/content", listOf("proxy", "vpn", "network")),
            entry("lyrics_romanization", R.string.lyrics_romanization, "settings/content/romanization", listOf("romanize", "romaji", "transliterate")),
            entry("lyrics_provider_selection", R.string.lyrics_provider_selection, "settings/content", listOf("provider", "lrclib", "kugou", "lyrics")),
            entry("lyrics_provider_priority", R.string.lyrics_provider_priority, "settings/content", listOf("priority", "order", "lyrics")),
            entry("set_quick_picks", R.string.set_quick_picks, "settings/content", listOf("quick picks", "home", "recommendations")),
            entry("randomize_home_order", R.string.randomize_home_order, "settings/content", listOf("random", "shuffle", "home")),
            // AI
            entry("ai_provider", R.string.ai_provider, "settings/ai", listOf("openai", "claude", "gemini", "provider")),
            entry("ai_api_key", R.string.ai_api_key, "settings/ai", listOf("key", "token", "secret", "api")),
            entry("ai_model", R.string.ai_model, "settings/ai", listOf("model", "gpt", "llm")),
            entry("ai_translation_mode", R.string.ai_translation_mode, "settings/ai", listOf("literal", "transcribed", "mode")),
            entry("ai_target_language", R.string.ai_target_language, "settings/ai", listOf("language", "target", "translate")),
            entry("ai_system_prompt", R.string.ai_system_prompt, "settings/ai", listOf("prompt", "system", "instruction")),
            // DJ (navigate only — provider key/model UI owned elsewhere; still searchable)
            entry("gemini_nano_enable", R.string.gemini_nano_enable, "settings/dj", listOf("nano", "gemini", "on-device", "dj")),
            entry("nano_dj_speak", R.string.nano_dj_speak, "settings/dj", listOf("speak", "voice", "talk", "dj")),
            entry("dj_ai_provider", R.string.dj_ai_provider, "settings/dj", listOf("provider", "openai", "anthropic", "groq", "openrouter")),
            // Android Auto
            entry("android_auto_visible_sections", R.string.android_auto_visible_sections, "settings/android_auto", listOf("sections", "browse", "order", "reorder", "visible", "liked", "songs")),
            entry("android_auto_target_playlist", R.string.android_auto_target_playlist, "settings/android_auto", listOf("quick add", "save", "destination", "playlist")),
            entry("android_auto_youtube_playlists", R.string.android_auto_youtube_playlists, "settings/android_auto", listOf("youtube", "suggested", "mixes", "playlists")),
            entry("android_auto_search_local_songs_limit", R.string.android_auto_search_local_songs_limit, "settings/android_auto", listOf("search", "limit", "local", "songs")),
            // Player
            entry("audio_quality", R.string.audio_quality, "settings/player", listOf("quality", "bitrate", "high", "low")),
            entry("crossfade", R.string.crossfade, "settings/player", listOf("fade", "transition", "blend")),
            entry("skip_silence", R.string.skip_silence, "settings/player", listOf("silence", "gap", "quiet")),
            entry("audio_normalization", R.string.audio_normalization, "settings/player", listOf("normalize", "loudness", "volume")),
            entry("audio_offload", R.string.audio_offload, "settings/player", listOf("offload", "hardware", "battery")),
            entry("google_cast", R.string.google_cast, "settings/player", listOf("cast", "chromecast", "tv")),
            entry("persistent_queue", R.string.persistent_queue, "settings/player", listOf("queue", "persist", "restore")),
            entry("autoplay", R.string.autoplay, "settings/player", listOf("auto", "continue", "play")),
            entry("persistent_shuffle_title", R.string.persistent_shuffle_title, "settings/player", listOf("shuffle", "random")),
            entry("auto_download_on_like", R.string.auto_download_on_like, "settings/player", listOf("download", "like", "offline")),
            entry("enable_automatic_sleeptimer", R.string.enable_automatic_sleeptimer, "settings/player", listOf("sleep", "timer", "bed")),
            entry("resume_on_bluetooth_connect", R.string.resume_on_bluetooth_connect, "settings/player", listOf("bluetooth", "headset", "resume")),
            // Privacy
            entry("pause_listen_history", R.string.pause_listen_history, "settings/privacy", listOf("history", "pause", "listen")),
            entry("clear_listen_history", R.string.clear_listen_history, "settings/privacy", listOf("clear", "history", "listen")),
            entry("pause_search_history", R.string.pause_search_history, "settings/privacy", listOf("search", "history", "pause")),
            entry("clear_search_history", R.string.clear_search_history, "settings/privacy", listOf("clear", "search", "history")),
            entry("disable_screenshot", R.string.disable_screenshot, "settings/privacy", listOf("screenshot", "secure", "flag")),
            // Storage
            entry("enable_song_cache", R.string.enable_song_cache, "settings/storage", listOf("cache", "song", "enable")),
            entry("max_song_cache_size", R.string.max_song_cache_size, "settings/storage", listOf("cache", "size", "limit")),
            entry("clear_song_cache", R.string.clear_song_cache, "settings/storage", listOf("clear", "cache", "song")),
            entry("max_image_cache_size", R.string.max_image_cache_size, "settings/storage", listOf("image", "cache", "artwork")),
            entry("clear_image_cache", R.string.clear_image_cache, "settings/storage", listOf("clear", "image", "cache")),
            entry("clear_all_downloads", R.string.clear_all_downloads, "settings/storage", listOf("download", "clear", "offline")),
            // Backup
            entry("action_backup", R.string.action_backup, "settings/backup_restore", listOf("backup", "export", "save")),
            entry("action_restore", R.string.action_restore, "settings/backup_restore", listOf("restore", "import", "load")),
            entry("import_csv", R.string.import_csv, "settings/backup_restore", listOf("csv", "exportify", "spotify", "import")),
            // Integrations
            entry("discord_integration", R.string.discord_integration, "settings/integrations/discord", listOf("discord", "rpc", "presence")),
            entry("enable_discord_rpc", R.string.enable_discord_rpc, "settings/integrations/discord", listOf("discord", "rpc", "presence", "rich")),
            entry("lastfm_integration", R.string.lastfm_integration, "settings/integrations/lastfm", listOf("lastfm", "scrobble")),
            entry("enable_scrobbling", R.string.enable_scrobbling, "settings/integrations/lastfm", listOf("scrobble", "lastfm")),
            entry("spotify_integration", R.string.spotify_integration, "settings/integrations/spotify", listOf("spotify", "taste", "exportify")),
            entry("spotify_client_id", R.string.spotify_client_id, "settings/integrations/spotify", listOf("spotify", "client", "oauth", "api")),
            entry("listen_together", R.string.listen_together, "settings/integrations/listen_together", listOf("together", "room", "party", "sync")),
            // Updater
            entry("check_for_updates", R.string.check_for_updates, "settings/updater", listOf("update", "check", "version")),
            entry("update_notifications", R.string.update_notifications, "settings/updater", listOf("notification", "update")),
            // Account (hub dialog)
            entry("yt_sync", R.string.yt_sync, "settings", listOf("sync", "account", "youtube", "library")),
            entry("advanced_login", R.string.advanced_login, "settings", listOf("token", "login", "cookie", "sapisid")),
        )

    private fun entry(
        id: String,
        @StringRes titleRes: Int,
        route: String,
        synonyms: List<String> = emptyList(),
        keywords: List<String> = emptyList(),
    ) = SettingsSearchEntry(id, titleRes, route, synonyms, keywords)
}

@Composable
fun rememberSettingsSearchSuggestions(query: String, limit: Int = 8): List<SettingsSearchSuggestion> {
    val context = LocalContext.current
    val trimmed = query.trim()
    return remember(trimmed, limit) {
        if (trimmed.isEmpty()) {
            emptyList()
        } else {
            SettingsSearchCatalog.entries
                .mapNotNull { entry ->
                    val title = context.getString(entry.titleRes)
                    if (!settingsEntryMatches(trimmed, title, entry.synonyms, entry.keywords)) {
                        return@mapNotNull null
                    }
                    val sectionLabel =
                        entry.route
                            .removePrefix("settings/")
                            .substringBefore('/')
                            .replace('_', ' ')
                            .replaceFirstChar { it.uppercase() }
                            .takeIf { it.isNotBlank() && !title.equals(it, ignoreCase = true) }
                    SettingsSearchSuggestion(entry, title, sectionLabel)
                }.distinctBy { it.entry.id }
                .sortedBy { it.title.lowercase() }
                .take(limit)
        }
    }
}
