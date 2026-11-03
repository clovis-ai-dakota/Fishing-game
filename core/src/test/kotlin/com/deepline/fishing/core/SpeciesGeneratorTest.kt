package com.deepline.fishing.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Species ids are saved in players' fish logs: the generated species must never change, or
 * players would lose catches. If this fails, revert the generator change (see CLAUDE.md).
 */
class SpeciesGeneratorTest {
    @Test
    fun generatedSpeciesNeverChange() {
        assertEquals("sunny_goby", FishCatalog.all[16].id)
        assertEquals("ancient_tonguefish", FishCatalog.all[198].id)
        assertEquals(-1559898339, FishCatalog.all.joinToString(",") { it.id }.hashCode())
    }

    @Test
    fun everySpeciesLivesInItsZoneAndIsWorthMoreDeeper() {
        for (sp in FishCatalog.all) {
            assertEquals(true, sp.minDepth < sp.maxDepth, sp.id)
            assertEquals(true, sp.basePoints > 0 && sp.weightKg > 0f, sp.id)
        }
        // On average, deeper zones pay more.
        val avg = DepthZone.entries.map { z -> FishCatalog.regular.filter { it.zone == z }.map { it.basePoints }.average() }
        assertEquals(avg.sorted(), avg)
    }
}
