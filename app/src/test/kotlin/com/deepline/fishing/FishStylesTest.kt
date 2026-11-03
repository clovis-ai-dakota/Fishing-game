package com.deepline.fishing

import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.ui.FishShape
import com.deepline.fishing.ui.FishStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishStylesTest {
    /** Only lures shine through dark water, and "glowing" secrets rely on seeing them. */
    @Test
    fun everyGlowingFishHasALure() {
        for (sp in FishCatalog.all.filter { it.glowing }) {
            assertEquals("${sp.name} glows but has no lure", FishShape.ANGLER, FishStyles.of(sp).shape)
        }
    }

    @Test
    fun theGeneratedSpeciesDontAllLookAlike() {
        val styles = FishCatalog.all.map { FishStyles.of(it) }
        assertTrue(styles.map { it.shape }.toSet().size == FishShape.entries.size)
        assertTrue(styles.toSet().size >= 12)
    }
}
