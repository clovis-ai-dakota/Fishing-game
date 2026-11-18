package com.deepline.fishing.render3d

import com.deepline.fishing.ui.render3d.ModelAssets
import com.deepline.fishing.ui.render3d.ModelCredits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The CC0 models: every file is credited, loads soundly, and the whole set stays small. */
class ModelAssetsTest {
    private val dir = File("src/main/assets/models")

    @Test
    fun everyModelIsCreditedLoadsAndFitsTheBudget() {
        val models = dir.listFiles { f -> f.extension == "dlm" }!!.map { it.nameWithoutExtension }.toSet()
        val textures = File(dir, "tex").listFiles()!!.map { it.name }.toSet()
        val credited = ModelCredits.all.flatMap { it.models }.toSet()
        assertEquals("models credited in ModelCredits", models, credited)
        assertEquals("textures credited", textures, ModelCredits.all.flatMap { it.textures }.toSet())
        val md = File(dir, "CREDITS.md").readText()
        for (name in models + textures) assertTrue("$name isn't in CREDITS.md", name in md)
        for (c in ModelCredits.all) assertTrue(c.url in md)
        assertTrue("CC0 only", ModelCredits.LICENCE.startsWith("CC0"))
        for (name in models) {
            val m = ModelAssets.model(name)
            assertTrue("$name is empty", m.pieces.isNotEmpty())
            for (p in m.pieces) {
                assertTrue(p.mesh.indices.all { it in 0 until p.mesh.vertexCount })
                assertTrue(p.mesh.positions.all { it.isFinite() })
                p.texture?.let { assertTrue("$name uses a missing texture $it", it in textures) }
            }
        }
        // A small share of the app: under 1.2 MB in all.
        val bytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        assertTrue("models take $bytes bytes", bytes < 1_200_000)
    }
}
