package com.deepline.fishing.core

enum class DepthZone(val displayName: String, val minDepth: Float, val maxDepth: Float) {
    SHALLOWS("Sunlit Shallows", 0f, 100f),
    TWILIGHT("Twilight Reef", 100f, 300f),
    MIDNIGHT("Midnight Trench", 300f, 500f),
    ABYSS("Abyssal Floor", 500f, Float.MAX_VALUE);

    companion object {
        fun of(depth: Float): DepthZone = entries.first { depth < it.maxDepth }
    }
}

/**
 * A kind of fish. Colors are ARGB values so the UI layer can render them without the core
 * depending on any graphics library.
 */
data class FishSpecies(
    val id: String,
    val name: String,
    val weightKg: Float,
    val basePoints: Int,
    val minDepth: Float,
    val maxDepth: Float,
    val lengthM: Float,
    val heightRatio: Float,
    val speed: Float,
    /** Relative spawn weight among species that live at a given depth. */
    val rarity: Int,
    val bodyColor: Long,
    val accentColor: Long,
) {
    val zone: DepthZone get() = DepthZone.of(minDepth)

    /** In-game body length (catalog length × [GameConfig.FISH_SIZE_SCALE]). */
    val bodyLengthM: Float get() = lengthM * GameConfig.FISH_SIZE_SCALE
}

object FishCatalog {
    val all: List<FishSpecies> = listOf(
        // Sunlit Shallows
        FishSpecies("silver_sprat", "Silver Sprat", 0.2f, 5, 0f, 90f, 0.7f, 0.38f, 2.0f, 30, 0xFFC9D6DF, 0xFF7E929F),
        FishSpecies("coral_darter", "Coral Darter", 0.4f, 8, 0f, 110f, 0.8f, 0.42f, 2.6f, 25, 0xFFFF8A65, 0xFFFFD54F),
        FishSpecies("reed_perch", "Reed Perch", 0.9f, 12, 10f, 140f, 1.1f, 0.42f, 1.6f, 20, 0xFF9CCC65, 0xFF33691E),
        FishSpecies("sun_bream", "Sun Bream", 1.6f, 18, 30f, 160f, 1.4f, 0.55f, 1.3f, 12, 0xFFFFCA28, 0xFFF57F17),
        // Twilight Reef
        FishSpecies("dusk_snapper", "Dusk Snapper", 2.4f, 30, 100f, 280f, 1.5f, 0.45f, 1.8f, 18, 0xFFE57373, 0xFF8E2424),
        FishSpecies("ribbon_eel", "Ribbon Eel", 1.8f, 34, 120f, 320f, 2.6f, 0.15f, 1.4f, 12, 0xFF4DB6AC, 0xFF00695C),
        FishSpecies("reef_grouper", "Reef Grouper", 5.0f, 45, 140f, 320f, 1.9f, 0.5f, 0.9f, 10, 0xFF7986CB, 0xFF283593),
        FishSpecies("veil_ray", "Veil Ray", 6.5f, 55, 180f, 340f, 2.2f, 0.3f, 1.1f, 6, 0xFFB39DDB, 0xFF4527A0),
        // Midnight Trench
        FishSpecies("ghost_hake", "Ghost Hake", 4.0f, 70, 300f, 480f, 1.7f, 0.35f, 1.2f, 16, 0xFFE0E0E0, 0xFF9E9E9E),
        FishSpecies("needle_viper", "Needle Viper", 3.0f, 85, 320f, 520f, 1.6f, 0.25f, 2.2f, 12, 0xFF26C6DA, 0xFF004D57),
        FishSpecies("ink_cuttle", "Ink Cuttle", 2.6f, 92, 330f, 520f, 1.2f, 0.5f, 0.8f, 10, 0xFF8D6E63, 0xFF3E2723),
        FishSpecies("iron_sturgeon", "Iron Sturgeon", 12f, 120, 360f, 540f, 3.0f, 0.3f, 0.7f, 6, 0xFF78909C, 0xFF37474F),
        // Abyssal Floor
        FishSpecies("glowfin_angler", "Glowfin Angler", 5f, 150, 480f, 800f, 1.4f, 0.6f, 0.6f, 14, 0xFF5C6BC0, 0xFFB2FF59),
        FishSpecies("star_drifter", "Star Drifter", 1.2f, 180, 500f, 800f, 1.0f, 0.6f, 0.9f, 10, 0xFFF48FB1, 0xFFFFF59D),
        FishSpecies("abyss_wyrm", "Abyss Wyrm", 10f, 220, 520f, 800f, 3.4f, 0.18f, 1.3f, 6, 0xFF6A1B9A, 0xFFEA80FC),
        FishSpecies("pale_leviathan", "Pale Leviathan", 24f, 450, 560f, 800f, 4.5f, 0.32f, 0.5f, 2, 0xFFECEFF1, 0xFF80DEEA),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): FishSpecies? = byId[id]
}

/**
 * Things in the water that aren't fish. Touching one on the way down costs a bump; the last bump
 * snags it (the only way to land one), worth [snagPoints] base points (more the deeper it is).
 */
enum class ObstacleKind(
    val displayName: String,
    val minDepth: Float,
    val maxDepth: Float,
    val snagPoints: Int,
    val weightKg: Float,
) {
    DRIFTWOOD("Driftwood", 0f, 260f, snagPoints = 100, weightKg = 2f),
    ROCK("Rock", 20f, Float.MAX_VALUE, snagPoints = 200, weightKg = 4f),
    JELLY("Jelly cluster", 120f, Float.MAX_VALUE, snagPoints = 150, weightKg = 1f),
}
