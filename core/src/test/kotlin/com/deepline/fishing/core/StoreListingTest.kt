package com.deepline.fishing.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The Play store listing kit (docs/store/listings, in the fastlane "supply" layout) stays within
 * Play's limits and metadata policy in every language: title 30 characters, short description 80,
 * full description 4000, release notes 500; no ranking claims ("best", "#1"...), no emoji in the
 * title, no fishing secret named; the same screenshot captions in every language, short enough to
 * read on a phone. Prints the counts (see docs/store/README.md).
 */
class StoreListingTest {
    private fun repoDir(path: String): File =
        listOf(File("../$path"), File(path)).firstOrNull { it.isDirectory } ?: error("Can't find $path")

    private val root by lazy { repoDir("docs/store/listings") }
    private val locales = listOf("en-US", "fr-FR", "es-ES", "de-DE", "pt-BR", "tr-TR")

    private fun text(locale: String, name: String): String {
        val f = File(File(root, locale), name)
        if (!f.isFile) fail("$locale is missing $name")
        return f.readText().trim()
    }

    /** Play counts characters, not bytes: code points (é, ı and ç count once). */
    private fun length(s: String) = s.codePointCount(0, s.length)

    private fun captions(locale: String) = text(locale, "screenshot_captions.txt").lines().map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Ranking and performance claims the metadata policy forbids, by language. Mode names that
     * contain "best" ("Best of 5") are allowed and removed before checking.
     */
    private val claims = mapOf(
        "en-US" to listOf("best", "#1", "number one", "no. 1", "top", "top-rated", "popular", "app of the year", "free"),
        "fr-FR" to listOf("meilleur", "meilleure", "n°1", "numéro 1", "numéro un", "populaire", "gratuit"),
        "es-ES" to listOf("mejor", "número 1", "nº 1", "popular", "gratis"),
        "de-DE" to listOf("beste", "bester", "bestes", "nummer 1", "nr. 1", "beliebt", "kostenlos"),
        "pt-BR" to listOf("melhor", "número 1", "nº 1", "popular", "grátis"),
        "tr-TR" to listOf("en iyi", "1 numara", "popüler", "ücretsiz", "bedava"),
    )
    private val modeNames = listOf("best of 5", "au meilleur des 5", "al mejor de 5", "melhor de 5", "5'te en iyi")

    private fun containsWord(text: String, word: String): Boolean {
        val w = Regex.escape(word.lowercase())
        return Regex("(^|[^\\p{L}\\p{N}])$w($|[^\\p{L}\\p{N}])").containsMatchIn(text.lowercase())
    }

    private fun withoutModeNames(s: String) = modeNames.fold(s.lowercase()) { acc, m -> acc.replace(m, " ") }

    @Test
    fun everyLanguageFitsPlaysLimits() {
        println("locale  title short  full  notes  captions")
        for (locale in locales) {
            val title = text(locale, "title.txt")
            val short = text(locale, "short_description.txt")
            val full = text(locale, "full_description.txt")
            val notes = text(locale, "changelogs/1.txt")
            println("%-7s %5d %5d %5d %6d  %d".format(locale, length(title), length(short), length(full), length(notes), captions(locale).size))
            assertTrue(length(title) <= 30, "$locale title is ${length(title)} characters (30 max): $title")
            assertTrue(length(short) <= 80, "$locale short description is ${length(short)} characters (80 max)")
            assertTrue(length(full) <= 4000, "$locale full description is ${length(full)} characters (4000 max)")
            assertTrue(length(notes) <= 500, "$locale release notes are ${length(notes)} characters (500 max)")
            assertTrue(title.startsWith("Deep Line"), "$locale title keeps the brand first")
            assertTrue(title.lines().size == 1 && short.lines().size == 1, "$locale title and short description are one line")
            assertTrue(short !in full, "$locale: the full description shouldn't repeat the short one")
        }
    }

    @Test
    fun noRankingClaimsEmojiOrSecrets() {
        // Emoji and pictographs (the title must have none; descriptions keep to plain bullets).
        val emoji = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B50}\\x{2B55}]")
        for (locale in locales) {
            val texts = listOf("title.txt", "short_description.txt", "full_description.txt", "changelogs/1.txt", "screenshot_captions.txt")
                .associateWith { text(locale, it) }
            for ((name, t) in texts) {
                assertTrue(!emoji.containsMatchIn(t), "$locale $name has an emoji")
                val checked = withoutModeNames(t)
                for (word in claims.getValue(locale)) {
                    assertTrue(!containsWord(checked, word), "$locale $name makes a claim Play forbids: \"$word\"")
                }
            }
        }
        // Fishing secrets are hidden mechanics: the listing may say they exist, never name one.
        val english = locales.joinToString("\n") { l -> text(l, "full_description.txt") + text(l, "short_description.txt") }
        for (id in Secrets.all) {
            val name = id.replace('_', ' ')
            assertTrue(!containsWord(english, name), "the listing names the secret \"$name\"")
        }
    }

    @Test
    fun screenshotCaptionsMatchThePlanInEveryLanguage() {
        val count = captions("en-US").size
        assertTrue(count in 6..8, "6 to 8 screenshots")
        val plan = File(root.parentFile, "screenshots.md").readText()
        for (locale in locales) {
            val c = captions(locale)
            assertEquals(count, c.size, "$locale has a caption for every screenshot")
            for (line in c) {
                assertTrue(length(line) <= 32, "$locale caption too long to read on a phone (32 max): $line")
                assertTrue(line in plan, "docs/store/screenshots.md shows the $locale caption \"$line\"")
            }
        }
    }

    @Test
    fun theNumbersMatchTheGame() {
        // The listing quotes the game's numbers: keep them in step with GameConfig and the catalogue.
        val dark = "${GameConfig.DARK_DEPTH.toInt()} m"
        val species = FishCatalog.all.size.toString()
        for (locale in locales) {
            assertTrue(dark in captions(locale)[3], "$locale caption 4 says the dark starts at $dark")
            assertTrue(dark in text(locale, "full_description.txt"), "$locale description says the dark starts at $dark")
            assertTrue(species in text(locale, "short_description.txt"), "$locale short description counts $species species")
            assertTrue("${GameConfig.DESCENT_TOUCH_LIMIT}" in text(locale, "full_description.txt"), "$locale description has the bump count")
        }
    }
}
