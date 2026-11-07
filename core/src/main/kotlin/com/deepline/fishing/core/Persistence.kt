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
        appendLine("luckyCharms=${p.luckyCharms}")
        appendLine("sparkleLures=${p.sparkleLures}")
        appendLine("detectors=${p.detectors}")
        appendLine("ownedItems=${p.ownedItems.joinToString(",")}")
        appendLine("clubUntil=${p.clubUntilMs}")
        appendLine("clubPouchNext=${p.clubPouchNextMs}")
        appendLine("season=${p.seasonNumber},${p.seasonXp}")
        appendLine("seasonClaims=${p.seasonClaims.sorted().joinToString(",")}")
        appendLine("event=${p.eventKey},${p.eventProgress}")
        appendLine("eventClaims=${p.eventClaims.sorted().joinToString(",")}")
        appendLine("titleFrame=${p.titleFrame}")
        appendLine("menuTheme=${p.menuTheme}")
        appendLine("nightMode=${p.nightMode}")
        appendLine("cardStyle=${p.cardStyle}")
        appendLine("tankFish=${p.tankFish.joinToString(",")}")
        appendLine("hiddenDecor=${p.hiddenDecor.sorted().joinToString(",")}")
        appendLine("sessions=${p.sessions}")
        appendLine("starterOfferEnds=${p.starterOfferEndsMs}")
        appendLine("casts=${p.castsMade}")
        appendLine("battle=${p.battleWins},${p.battleLosses},${p.battleDraws}")
        appendLine("secrets=${p.discoveredSecrets.sorted().joinToString(",")}")
        appendLine("secretsMuted=${p.mutedSecretPopups.sorted().joinToString(",")}")
        appendLine("snagged=${p.objectsSnagged}")
        appendLine("seen=${p.seenSpecies.sorted().joinToString(",")}")
        appendLine("skins=${p.equippedSkins.entries.joinToString(",") { "${it.key.name}:${it.value.name}" }}")
        appendLine("boat=${encodeDesign(p.boatDesign)}")
        appendLine("customBoat=${if (p.customBoatEquipped) 1 else 0}")
        appendLine("boatParts=${p.unlockedBoatParts.sorted().joinToString(",")}")
        appendLine("atmosphereUnlocked=${if (p.atmosphereUnlocked) 1 else 0}")
        appendLine("atmosphere=${p.atmosphere.name}")
        // -- Daily loop: new lines below.
        appendLine("daily=${p.dailyStreak},${p.dailyBestStreak},${p.dailyLastClaimDay ?: ""},${p.dailyClaims},${p.streakShields}")
        appendLine("challengeDay=${p.challengeDay ?: ""},${p.challengeRerolls}")
        for (c in p.dailyChallenges) {
            appendLine(
                "challenge.${c.difficulty.name}=${c.kind.name},${c.target},${c.zone?.name ?: ""},${c.speciesId ?: ""}," +
                    "${c.progress},${if (c.claimed) 1 else 0}",
            )
        }

        // -- Social: new lines below.

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
                key == "luckyCharms" -> value.toIntOrNull()?.let { p = p.copy(luckyCharms = it) }
                key == "sparkleLures" -> value.toIntOrNull()?.let { p = p.copy(sparkleLures = it) }
                key == "detectors" -> value.toIntOrNull()?.let { p = p.copy(detectors = it) }
                key == "ownedItems" -> p = p.copy(ownedItems = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                key == "clubUntil" -> value.toLongOrNull()?.let { p = p.copy(clubUntilMs = it) }
                key == "clubPouchNext" -> value.toLongOrNull()?.let { p = p.copy(clubPouchNextMs = it) }
                key == "season" -> value.split(',').let { v ->
                    val n = v.getOrNull(0)?.toIntOrNull()
                    val xp = v.getOrNull(1)?.toIntOrNull()
                    if (n != null && xp != null) p = p.copy(seasonNumber = n, seasonXp = xp)
                }
                key == "seasonClaims" -> p = p.copy(seasonClaims = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                key == "event" -> value.split(',').let { v ->
                    val progress = v.getOrNull(1)?.toIntOrNull()
                    if (v.size == 2 && progress != null) p = p.copy(eventKey = v[0], eventProgress = progress)
                }
                key == "eventClaims" -> p = p.copy(eventClaims = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                key == "titleFrame" -> p = p.copy(titleFrame = TitleFrame.of(value.trim()).name)
                key == "menuTheme" -> p = p.copy(menuTheme = MenuTheme.of(value.trim()).name)
                key == "nightMode" -> p = p.copy(nightMode = NightMode.of(value.trim()).name)
                key == "cardStyle" -> p = p.copy(cardStyle = CardStyle.of(value.trim()).name)
                key == "tankFish" -> p = p.copy(tankFish = value.split(',').map { it.trim() }.filter { FishCatalog.byId(it) != null })
                key == "hiddenDecor" -> p = p.copy(hiddenDecor = value.split(',').map { it.trim() }.filter { n -> Decor.entries.any { it.name == n } }.toSet())
                key == "sessions" -> value.toIntOrNull()?.let { p = p.copy(sessions = it) }
                key == "starterOfferEnds" -> value.toLongOrNull()?.let { p = p.copy(starterOfferEndsMs = it) }
                key == "secrets" -> p = p.copy(discoveredSecrets = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                key == "secretsMuted" -> p = p.copy(mutedSecretPopups = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                key == "seen" -> p = p.copy(seenSpecies = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                key == "snagged" -> value.toIntOrNull()?.let { p = p.copy(objectsSnagged = it) }
                key == "skins" -> p = p.copy(
                    equippedSkins = value.split(',').mapNotNull { entry ->
                        val (slot, cosmetic) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                        val s = CosmeticSlot.entries.firstOrNull { it.name == slot }
                        val c = Cosmetic.entries.firstOrNull { it.name == cosmetic }
                        if (s != null && c != null && c.slot == s) s to c else null
                    }.toMap(),
                )
                key == "boat" -> decodeDesign(value)?.let { p = p.copy(boatDesign = it) }
                key == "customBoat" -> p = p.copy(customBoatEquipped = value.trim() == "1")
                key == "boatParts" -> p = p.copy(
                    unlockedBoatParts = value.split(',').map { it.trim() }.filter { BoatPart.fromKey(it) != null }.toSet(),
                )
                key == "atmosphereUnlocked" -> p = p.copy(atmosphereUnlocked = value.trim() == "1")
                key == "atmosphere" -> Atmosphere.entries.firstOrNull { it.name == value.trim() }?.let { p = p.copy(atmosphere = it) }
                // -- Daily loop: new keys below.
                key == "daily" -> value.split(',').map { it.trim() }.takeIf { it.size == 5 }?.let { v ->
                    val numbers = listOf(v[0], v[1], v[3], v[4]).map { it.toIntOrNull() }
                    if (numbers.all { it != null && it >= 0 } && (v[2].isEmpty() || v[2].toLongOrNull() != null)) {
                        p = p.copy(
                            dailyStreak = numbers[0]!!, dailyBestStreak = numbers[1]!!, dailyLastClaimDay = v[2].toLongOrNull(),
                            dailyClaims = numbers[2]!!, streakShields = numbers[3]!!.coerceAtMost(GameConfig.MAX_STREAK_SHIELDS),
                        )
                    }
                }
                key == "challengeDay" -> value.split(',').map { it.trim() }.takeIf { it.size == 2 }?.let { v ->
                    val rerolls = v[1].toIntOrNull()
                    if (rerolls != null && (v[0].isEmpty() || v[0].toLongOrNull() != null)) {
                        p = p.copy(challengeDay = v[0].toLongOrNull(), challengeRerolls = rerolls)
                    }
                }
                key.startsWith("challenge.") -> decodeChallenge(key.removePrefix("challenge."), value)?.let { c ->
                    p = p.copy(dailyChallenges = (p.dailyChallenges.filter { it.difficulty != c.difficulty } + c).sortedBy { it.difficulty })
                }

                // -- Social: new keys below.

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
        // A saved Nightmare choice without the unlock (hand-edited save) falls back to Family.
        if (p.atmosphere == Atmosphere.NIGHTMARE && !p.atmosphereUnlocked) p = p.copy(atmosphere = Atmosphere.FAMILY)
        return p.copy(ropeLevels = ropeLevels, collection = collection)
    }

    /** A daily challenge: kind, target, zone, species, progress, claimed (unknown values: dropped). */
    private fun decodeChallenge(difficulty: String, value: String): DailyChallenge? {
        val v = value.split(',').map { it.trim() }
        if (v.size != 6) return null
        val zone = if (v[2].isEmpty()) null else DepthZone.entries.firstOrNull { it.name == v[2] } ?: return null
        val species = if (v[3].isEmpty()) null else v[3].takeIf { FishCatalog.byId(it) != null } ?: return null
        return DailyChallenge(
            difficulty = ChallengeDifficulty.entries.firstOrNull { it.name == difficulty } ?: return null,
            kind = ChallengeKind.entries.firstOrNull { it.name == v[0] } ?: return null,
            target = v[1].toIntOrNull()?.takeIf { it > 0 } ?: return null,
            zone = zone,
            speciesId = species,
            progress = v[4].toIntOrNull()?.coerceAtLeast(0) ?: return null,
            claimed = v[5] == "1",
        )
    }

    /** Colours as palette indexes, then the pattern, sail, flag and emblem names. */
    private fun encodeDesign(d: BoatDesign) =
        listOf(d.hull, d.hullBottom, d.trim, d.cabin, d.sail, d.flag).joinToString(",") +
            ",${d.pattern.name},${d.sailStyle.name},${d.flagStyle.name},${d.emblem.name},${d.cloth.name}"

    private fun decodeDesign(value: String): BoatDesign? {
        val parts = value.split(',').map { it.trim() }
        if (parts.size != 10 && parts.size != 11) return null
        val colors = parts.take(6).map { it.toIntOrNull()?.takeIf { i -> i in BoatPalette.swatches.indices } ?: return null }
        return BoatDesign(
            hull = colors[0], hullBottom = colors[1], trim = colors[2], cabin = colors[3], sail = colors[4], flag = colors[5],
            pattern = HullPattern.entries.firstOrNull { it.name == parts[6] } ?: return null,
            sailStyle = SailStyle.entries.firstOrNull { it.name == parts[7] } ?: return null,
            flagStyle = FlagStyle.entries.firstOrNull { it.name == parts[8] } ?: return null,
            emblem = Emblem.entries.firstOrNull { it.name == parts[9] } ?: return null,
            cloth = parts.getOrNull(10)?.let { n -> ClothPattern.entries.firstOrNull { it.name == n } } ?: ClothPattern.PLAIN,
        )
    }
}

class ProgressRepository(private val store: KeyValueStore) {
    fun load(): PlayerProgress = store.getString(KEY)?.let(ProgressCodec::decode) ?: PlayerProgress()

    fun save(progress: PlayerProgress) = store.putString(KEY, ProgressCodec.encode(progress))

    private companion object {
        const val KEY = "progress"
    }
}
