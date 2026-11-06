package com.deepline.fishing.feel

import com.deepline.fishing.core.InMemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeelSettingsTest {
    @Test
    fun defaultsWhenNothingIsSaved() {
        val s = FeelSettings.load(InMemoryStore())
        assertEquals(FeelSettings(), s)
        assertTrue(s.haptics)
        assertFalse(s.reduceMotion)
    }

    @Test
    fun theSettingsAreSavedAndRestored() {
        val store = InMemoryStore()
        val s = FeelSettings(master = 0.25f, music = 0f, sfx = 1f, haptics = false, reduceMotion = true)
        s.save(store)
        assertEquals(s, FeelSettings.load(store))
        // Kept with the device preferences, under their own keys.
        assertEquals("false", store.getString(FeelSettings.KEY_HAPTICS))
        assertEquals("0.25", store.getString(FeelSettings.KEY_MASTER))
    }

    @Test
    fun reduceMotionFollowsThePhoneUntilThePlayerChooses() {
        val store = InMemoryStore()
        assertTrue(FeelSettings.load(store, systemReduceMotion = true).reduceMotion)
        FeelSettings(reduceMotion = false).save(store)
        assertFalse(FeelSettings.load(store, systemReduceMotion = true).reduceMotion)
    }

    @Test
    fun badValuesFallBackToSafeOnes() {
        val store = InMemoryStore()
        store.putString(FeelSettings.KEY_MASTER, "7")
        store.putString(FeelSettings.KEY_MUSIC, "NaN")
        store.putString(FeelSettings.KEY_SFX, "loud")
        store.putString(FeelSettings.KEY_HAPTICS, "maybe")
        val s = FeelSettings.load(store)
        assertEquals(1f, s.master, 0f)
        assertEquals(FeelSettings.DEFAULT_MUSIC, s.music, 0f)
        assertEquals(FeelSettings.DEFAULT_SFX, s.sfx, 0f)
        assertTrue(s.haptics)
    }
}
