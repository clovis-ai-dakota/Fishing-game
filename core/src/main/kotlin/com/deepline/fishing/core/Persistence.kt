package com.deepline.fishing.core

/** Minimal storage abstraction so the core stays independent of Android. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class InMemoryStore : KeyValueStore {
    private val map = HashMap<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) {
        map[key] = value
    }
}

/** Encodes [PlayerProgress] as simple `key=value` lines. Unknown or malformed lines are ignored. */
object ProgressCodec {
    private const val VERSION = 1

    fun encode(p: PlayerProgress): String = buildString {
        appendLine("v=$VERSION")
        appendLine("balance=${p.pointsBalance}")
        appendLine("earned=${p.pointsEarned}")
        appendLine("claimed=${p.claimedMilestones.sorted().joinToString(",")}")
        appendLine("lanterns=${p.lanterns}")
        appendLine("casts=${p.castsMade}")
        appendLine("battle=${p.battleWins},${p.battleLosses},${p.battleDraws}")
        appendLine("secrets=${p.discoveredSecrets.sorted().joinToString(",")}")
        appendLine("secretsMuted=${p.mutedSecretPopups.sorted().joinToString(",")}")
        appendLine("snagged=${p.objectsSnagged}")
        appendLine("skins=${p.equippedSkins.entries.joinToString(",") { "${it.key.name}:${it.value.name}" }}")
        for ((stat, level) in p.ropeLevels) appendLine("rope.${stat.name}=$level")
        for ((id, e) in p.collection) {
            appendLine("fish.$id=${e.caught},${e.sparklingCaught},${e.deepestM},${e.bestPoints}")
        }
    }

    fun decode(text: String): PlayerProgress {
        var p = PlayerProgress()
        val ropeLevels = HashMap<RopeStat, Int>()
        val collection = HashMap<String, CollectionEntry>()
        for (line in text.lineSequence()) {
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq)
            val value = line.substring(eq + 1)
            when {
                key == "balance" -> value.toLongOrNull()?.let { p = p.copy(pointsBalance = it) }
                key == "earned" -> value.toLongOrNull()?.let { p = p.copy(pointsEarned = it) }
                key == "claimed" -> p = p.copy(
                    claimedMilestones = value.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
                )
                key == "lanterns" -> value.toIntOrNull()?.let { p = p.copy(lanterns = it) }
                key == "secrets" -> p = p.copy(discoveredSecrets = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                key == "secretsMuted" -> p = p.copy(mutedSecretPopups = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                key == "snagged" -> value.toIntOrNull()?.let { p = p.copy(objectsSnagged = it) }
                key == "skins" -> p = p.copy(
                    equippedSkins = value.split(',').mapNotNull { entry ->
                        val (slot, cosmetic) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                        val s = CosmeticSlot.entries.firstOrNull { it.name == slot }
                        val c = Cosmetic.entries.firstOrNull { it.name == cosmetic }
                        if (s != null && c != null && c.slot == s) s to c else null
                    }.toMap(),
                )
                key == "battle" -> {
                    val parts = value.split(',').map { it.trim().toIntOrNull() }
                    if (parts.size == 3 && parts.all { it != null }) {
                        p = p.copy(battleWins = parts[0]!!, battleLosses = parts[1]!!, battleDraws = parts[2]!!)
                    }
                }
                key == "casts" -> value.toIntOrNull()?.let { p = p.copy(castsMade = it) }
                key.startsWith("rope.") -> {
                    val stat = RopeStat.entries.firstOrNull { it.name == key.removePrefix("rope.") }
                    val level = value.toIntOrNull()
                    if (stat != null && level != null) ropeLevels[stat] = level.coerceIn(0, stat.maxLevel)
                }
                key.startsWith("fish.") -> {
                    val parts = value.split(',')
                    if (parts.size == 4) {
                        val entry = CollectionEntry(
                            caught = parts[0].toIntOrNull() ?: continue,
                            sparklingCaught = parts[1].toIntOrNull() ?: continue,
                            deepestM = parts[2].toFloatOrNull() ?: continue,
                            bestPoints = parts[3].toIntOrNull() ?: continue,
                        )
                        collection[key.removePrefix("fish.")] = entry
                    }
                }
            }
        }
        return p.copy(ropeLevels = ropeLevels, collection = collection)
    }
}

class ProgressRepository(private val store: KeyValueStore) {
    fun load(): PlayerProgress = store.getString(KEY)?.let(ProgressCodec::decode) ?: PlayerProgress()

    fun save(progress: PlayerProgress) = store.putString(KEY, ProgressCodec.encode(progress))

    private companion object {
        const val KEY = "progress"
    }
}
