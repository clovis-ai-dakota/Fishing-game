package com.deepline.fishing.audio

/**
 * Every sound effect, with its mix settings. Files live in `assets/audio/sfx` (CC0, see
 * `assets/audio/CREDITS.md`); a sound with several files picks one at random, never the same one
 * twice in a row.
 *
 * - [volume]: its level in the mix (files are peak-normalised, so this is the only gain).
 * - [pitchJitter] / [volumeJitter]: random variation per play (± that share), so repeated sounds
 *   don't sound machine-made. Musical sounds (jingles, the catch arpeggios) get none: detuning
 *   them would sound wrong.
 * - [maxVoices]: how many may overlap; a new one stops the oldest beyond that.
 * - [minGapMs]: a repeat sooner than this is dropped (no machine-gun stacking).
 */
enum class Sfx(
    val files: List<String>,
    val volume: Float,
    val pitchJitter: Float = 0.05f,
    val volumeJitter: Float = 0.1f,
    val maxVoices: Int = 2,
    val minGapMs: Long = 40,
) {
    CAST_SPLASH(listOf("cast_splash_1", "cast_splash_2", "cast_splash_3"), 0.8f, pitchJitter = 0.07f),
    SURFACE_SPLASH(listOf("surface_splash_1", "surface_splash_2"), 0.85f, pitchJitter = 0.06f, maxVoices = 1, minGapMs = 500),
    BUBBLE(listOf("bubble_1", "bubble_2", "bubble_3", "bubble_4"), 0.3f, pitchJitter = 0.15f, volumeJitter = 0.3f, minGapMs = 120),
    BUMP_FISH(listOf("bump_fish"), 0.75f, pitchJitter = 0.1f),
    BUMP_ROCK(listOf("bump_rock_1", "bump_rock_2"), 0.95f, pitchJitter = 0.08f),
    BUMP_WOOD(listOf("bump_wood_1", "bump_wood_2"), 0.9f, pitchJitter = 0.08f),
    BUMP_JELLY(listOf("bump_jelly_1", "bump_jelly_2"), 0.8f, pitchJitter = 0.1f),
    /** Layered under the last bump's own sound: a deep thud (the line is about to turn back). */
    BUMP_LAST(listOf("bump_last"), 1f, pitchJitter = 0.03f, volumeJitter = 0.05f, maxVoices = 1),
    SNAG(listOf("snag"), 0.8f, maxVoices = 1),
    CATCH(listOf("catch"), 0.7f, pitchJitter = 0f, volumeJitter = 0.05f, maxVoices = 3),
    CATCH_RARE(listOf("catch_rare"), 0.85f, pitchJitter = 0f, volumeJitter = 0f, maxVoices = 2),
    /** Layered over a catch of a Sparkling fish. */
    SPARKLE(listOf("sparkle"), 0.6f, pitchJitter = 0.04f),
    CATCH_LEGENDARY(listOf("catch_legendary"), 1f, pitchJitter = 0f, volumeJitter = 0f, maxVoices = 1),
    TOO_HEAVY(listOf("too_heavy"), 0.55f, pitchJitter = 0f, maxVoices = 1, minGapMs = 900),
    REEL_TICK(listOf("reel_tick_1", "reel_tick_2"), 0.3f, pitchJitter = 0.1f, volumeJitter = 0.25f, minGapMs = 60),
    FAST_REEL(listOf("fast_reel"), 0.7f, pitchJitter = 0.02f, maxVoices = 1, minGapMs = 500),
    RESULT_GOOD(listOf("result_good"), 0.75f, pitchJitter = 0f, volumeJitter = 0f, maxVoices = 1),
    RESULT_EMPTY(listOf("result_empty"), 0.6f, pitchJitter = 0f, volumeJitter = 0f, maxVoices = 1),
    CLAIM(listOf("claim_coins"), 0.8f, pitchJitter = 0.04f, maxVoices = 1, minGapMs = 150),
    /** A short rising jingle layered over claims. */
    REWARD(listOf("reward"), 0.6f, pitchJitter = 0f, volumeJitter = 0f, maxVoices = 1, minGapMs = 150),
    PURCHASE(listOf("purchase"), 0.7f, pitchJitter = 0f, volumeJitter = 0f, maxVoices = 1, minGapMs = 150),
    BATTLE_WIN(listOf("battle_win"), 0.85f, pitchJitter = 0f, volumeJitter = 0f, maxVoices = 1),
    BATTLE_LOSE(listOf("battle_lose"), 0.75f, pitchJitter = 0f, volumeJitter = 0f, maxVoices = 1),
    BATTLE_DRAW(listOf("battle_draw"), 0.75f, pitchJitter = 0f, volumeJitter = 0f, maxVoices = 1),
    /** One sound for every fishing secret: it never tells which one. */
    SECRET(listOf("secret"), 0.75f, pitchJitter = 0f, volumeJitter = 0f, maxVoices = 1, minGapMs = 300),
    LANTERN(listOf("lantern"), 0.6f, pitchJitter = 0.03f, maxVoices = 1, minGapMs = 300),
    UI_TAP(listOf("ui_tap"), 0.35f, pitchJitter = 0.04f, minGapMs = 50),
    UI_TOGGLE(listOf("ui_toggle"), 0.45f, pitchJitter = 0.03f, minGapMs = 50);

    /** Asset paths of this sound's files. */
    val assets: List<String> get() = files.map { "audio/sfx/$it.ogg" }
}

/**
 * The music: a menu track, and two layers for casts that crossfade with depth (a bright, calm
 * one near the surface, a darker one in the deep). Files live in `assets/audio/music`, each a
 * seamless loop.
 */
enum class MusicTrack(val file: String, val volume: Float) {
    MENU("menu", 0.55f),
    SHALLOWS("shallows", 0.6f),
    DEEP("deep", 0.7f);

    val asset: String get() = "audio/music/$file.ogg"
}
