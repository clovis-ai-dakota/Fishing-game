package com.deepline.fishing.feel

import com.deepline.fishing.core.KeyValueStore

/**
 * The player's sound and feel settings. They belong to this phone (like the tour answer), so
 * they're kept in the device's key-value store, not in the saved progress.
 */
data class FeelSettings(
    /** Volumes, 0..1. */
    val master: Float = DEFAULT_MASTER,
    val music: Float = DEFAULT_MUSIC,
    val sfx: Float = DEFAULT_SFX,
    val haptics: Boolean = true,
    /** No screen shake, slow motion or flashes. */
    val reduceMotion: Boolean = false,
) {
    fun save(store: KeyValueStore) {
        store.putString(KEY_MASTER, master.toString())
        store.putString(KEY_MUSIC, music.toString())
        store.putString(KEY_SFX, sfx.toString())
        store.putString(KEY_HAPTICS, haptics.toString())
        store.putString(KEY_REDUCE_MOTION, reduceMotion.toString())
    }

    companion object {
        const val DEFAULT_MASTER = 0.8f
        const val DEFAULT_MUSIC = 0.6f
        const val DEFAULT_SFX = 0.9f

        const val KEY_MASTER = "feel.masterVolume"
        const val KEY_MUSIC = "feel.musicVolume"
        const val KEY_SFX = "feel.sfxVolume"
        const val KEY_HAPTICS = "feel.haptics"
        const val KEY_REDUCE_MOTION = "feel.reduceMotion"

        /**
         * The saved settings. Reduce motion starts as the phone's own choice ([systemReduceMotion]:
         * animations turned off in the accessibility settings) until the player picks.
         */
        fun load(store: KeyValueStore, systemReduceMotion: Boolean = false): FeelSettings {
            fun volume(key: String, default: Float) = store.getString(key)?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: default
            return FeelSettings(
                master = volume(KEY_MASTER, DEFAULT_MASTER),
                music = volume(KEY_MUSIC, DEFAULT_MUSIC),
                sfx = volume(KEY_SFX, DEFAULT_SFX),
                haptics = store.getString(KEY_HAPTICS)?.toBooleanStrictOrNull() ?: true,
                reduceMotion = store.getString(KEY_REDUCE_MOTION)?.toBooleanStrictOrNull() ?: systemReduceMotion,
            )
        }
    }
}
