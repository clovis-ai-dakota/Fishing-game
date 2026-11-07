package com.deepline.fishing.audio

/**
 * Where every sound and music file comes from. Only CC0 (public domain) assets are used; each
 * entry is a pack or track on OpenGameArt, its author, and the game files made from it (trimmed,
 * filtered and re-encoded). Shown in Settings > Credits and mirrored in `assets/audio/CREDITS.md`;
 * `AudioAssetsTest` checks that every file in `assets/audio` is credited here.
 */
data class AudioCredit(
    val title: String,
    val author: String,
    val url: String,
    val licence: String,
    /** Game files (without extension) under `assets/audio/sfx` or `assets/audio/music`. */
    val files: List<String>,
    val music: Boolean = false,
)

object AudioCredits {
    const val CC0 = "CC0 1.0 (public domain)"

    val all: List<AudioCredit> = listOf(
        AudioCredit(
            "Underwater Theme", "Spring Spring", "https://opengameart.org/content/underwater-theme-1", CC0,
            listOf("menu"), music = true,
        ),
        AudioCredit(
            "Drifting", "Some Weirdo", "https://opengameart.org/content/drifting-0", CC0,
            listOf("shallows"), music = true,
        ),
        AudioCredit(
            "Deep Sea", "Umplix", "https://opengameart.org/content/deep-sea", CC0,
            listOf("deep"), music = true,
        ),
        AudioCredit(
            "Fisheefects", "You're Perfect Studio (sounds by Memoraphile)", "https://opengameart.org/content/fisheefects",
            "$CC0 (also offered under CC-BY 4.0 and OGA-BY 3.0)",
            listOf("cast_splash_1", "bump_fish", "catch", "catch_rare", "too_heavy", "fast_reel"),
        ),
        AudioCredit(
            "40 CC0 water / splash / slime SFX", "rubberduck", "https://opengameart.org/content/40-cc0-water-splash-slime-sfx", CC0,
            listOf("cast_splash_2", "cast_splash_3", "surface_splash_1", "surface_splash_2", "bubble_4", "bump_jelly_1", "bump_jelly_2"),
        ),
        AudioCredit(
            "75 CC0 breaking / falling / hit sfx", "rubberduck", "https://opengameart.org/content/75-cc0-breaking-falling-hit-sfx", CC0,
            listOf("bump_rock_1", "bump_rock_2", "bump_wood_1", "bump_wood_2", "bump_last"),
        ),
        AudioCredit(
            "Bubble Sound Effects", "BMacZero (Brian MacIntosh)", "https://opengameart.org/content/bubble-sound-effects", CC0,
            listOf("bubble_1", "bubble_2", "bubble_3"),
        ),
        AudioCredit(
            "Clock Wind Sounds", "BMacZero (Brian MacIntosh)", "https://opengameart.org/content/clock-wind-sounds", CC0,
            listOf("reel_tick_1", "reel_tick_2"),
        ),
        AudioCredit(
            "Interface Sounds", "Kenney", "https://opengameart.org/content/interface-sounds", CC0,
            listOf("sparkle", "purchase", "secret", "lantern", "ui_tap", "ui_toggle"),
        ),
        AudioCredit(
            "85 Short music jingles", "Kenney", "https://opengameart.org/content/85-short-music-jingles", CC0,
            listOf("catch_legendary", "result_good", "result_empty", "reward", "battle_win", "battle_lose", "battle_draw"),
        ),
        AudioCredit(
            "50 RPG sound effects", "Kenney", "https://opengameart.org/content/50-rpg-sound-effects", CC0,
            listOf("snag", "claim_coins"),
        ),
    )
}
