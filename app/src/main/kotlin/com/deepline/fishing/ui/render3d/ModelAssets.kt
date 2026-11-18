package com.deepline.fishing.ui.render3d

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The CC0 models the game uses (Kenney, Quaternius: see `assets/models/CREDITS.md` and
 * [ModelCredits]), converted once by `tools/assets/convert_models.py` into small `.dlm` files in
 * `assets/models`. A model has painted parts (colour per vertex, drawn with the painted material
 * like the hand-made ones) and leaf parts (a cut-out texture in `assets/models/tex`, drawn by the
 * leaf shader, [Program.LEAF]).
 */
object ModelAssets {
    /** A piece of a model: painted (texture null) or a leaf part with its texture's file name. */
    class Piece(val texture: String?, val mesh: MeshData)

    class Model(val name: String, val pieces: List<Piece>) {
        val painted: List<MeshData> get() = pieces.filter { it.texture == null }.map { it.mesh }
        val leaves: List<Piece> get() = pieces.filter { it.texture != null }

        /** The model's extent (all pieces). */
        fun bounds(): Pair<Vec3, Vec3> {
            var lo = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            var hi = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (p in pieces) {
                val (a, b) = p.mesh.bounds()
                lo = Vec3(minOf(lo.x, a.x), minOf(lo.y, a.y), minOf(lo.z, a.z))
                hi = Vec3(maxOf(hi.x, b.x), maxOf(hi.y, b.y), maxOf(hi.z, b.z))
            }
            return lo to hi
        }
    }

    /**
     * Opens a file under `assets/` ("models/x.dlm"). The app sets it to its AssetManager; unit tests
     * read the module's own `src/main/assets`.
     */
    @Volatile var open: (String) -> InputStream? = { path ->
        listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path")).firstOrNull { it.exists() }?.inputStream()
    }

    private val cache = HashMap<String, Model>()

    fun model(name: String): Model = synchronized(cache) {
        cache.getOrPut(name) {
            val bytes = open("models/$name.dlm")?.use { it.readBytes() } ?: error("Missing model $name")
            read(name, bytes)
        }
    }

    internal fun read(name: String, bytes: ByteArray): Model {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { b.get(it) }
        require(String(magic) == "DLM1") { "$name isn't a model" }
        val count = b.int
        val pieces = (0 until count).map {
            val kind = b.get().toInt()
            val nameLen = b.short.toInt() and 0xFFFF
            val tex = ByteArray(nameLen).also { b.get(it) }.toString(Charsets.UTF_8)
            val v = b.int
            val n = b.int
            fun floats(k: Int) = FloatArray(k).also { a -> for (i in a.indices) a[i] = b.float }
            val pos = floats(v * 3)
            val nor = floats(v * 3)
            val uv = floats(v * 2)
            val col = floats(v * 4)
            val idx = IntArray(n).also { a -> for (i in a.indices) a[i] = b.int }
            val part = if (kind == 1) Part.LEAF else Part.PAINTED
            Piece(if (kind == 1) tex else null, MeshData(pos, nor, uv, FloatArray(v) { part }, col, idx))
        }
        return Model(name, pieces)
    }
}

/** Where every model comes from: shown in Settings > Credits, checked by `ModelAssetsTest`. */
object ModelCredits {
    class Credit(val pack: String, val author: String, val url: String, val models: List<String>, val textures: List<String> = emptyList())

    const val LICENCE = "CC0 1.0 (public domain)"

    val all = listOf(
        Credit(
            "Pirate Kit", "Kenney", "https://kenney.nl/assets/pirate-kit",
            listOf("palm-detailed-bend", "palm-detailed-straight", "barrel", "crate", "boat-row-small", "rocks-sand-a", "grass-plant"),
        ),
        Credit("Watercraft Kit", "Kenney", "https://kenney.nl/assets/watercraft-kit", listOf("buoy-flag")),
        Credit(
            "Stylized Nature MegaKit", "Quaternius", "https://quaternius.com/packs/stylizednaturemegakit.html",
            listOf(
                "CommonTree_3", "Bush_Common", "Flower_3_Group", "Rock_Medium_1", "Rock_Medium_2", "Pebble_Round_1", "Pebble_Round_2",
                "Pebble_Square_1", "Pebble_Square_2", "Plant_1", "Plant_7", "Grass_Common_Short",
            ),
            listOf("Flowers.png", "Leaves.png", "Leaves_Bush.png", "Leaves_NormalTree_C.png"),
        ),
    )
}
