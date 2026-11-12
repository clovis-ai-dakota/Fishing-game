package com.deepline.fishing.i18n

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * No player-facing text typed into Kotlin: once a file is converted to string resources, it stays
 * converted. A plain JVM test that reads the sources of the files in [CONVERTED].
 *
 * It fails on a string literal with letters in a converted file:
 * - anywhere inside a `Text(...)` call or a text parameter (`title =`, `contentDescription =`...);
 * - anywhere else when it looks like words (a capital letter or a space), e.g. a message built in
 *   a view model.
 * It lets through literals without letters ("·", "×", "%", digits), lower_snake/dotted keys
 * (test tags, preference keys, analytics names, ids: "tab_fish", "tour.showAgain"), the
 * arguments of logging, analytics, test tags and errors, and lines marked `// i18n-ignore` with a
 * reason (a brand name, a player-typed default...).
 *
 * Area agents: convert a file completely, then add it to [CONVERTED] (see docs/localization.md).
 */
class HardcodedTextTest {
    private val sources = File("src/main/kotlin/com/deepline/fishing")

    @Test
    fun convertedFilesHaveNoHardcodedText() {
        val problems = ArrayList<String>()
        for (path in CONVERTED) {
            val file = File(sources, path)
            assertTrue("$path is in CONVERTED but doesn't exist", file.isFile)
            problems += findHardcodedText(path, file.readText())
        }
        if (problems.isNotEmpty()) fail("Hard-coded text in converted files (use string resources, see docs/localization.md):\n" + problems.joinToString("\n"))
    }

    @Test
    fun coreEnglishNamesAreNeverShown() {
        // Core's English names (displayName, title, setName, tagline, summary, euroLabel) are for the
        // server and the tests: the app shows CoreText's labels instead (ui/i18n/CoreText.kt).
        val pattern = Regex("\\.(displayName|setName|tagline|summary|euroLabel)\\b|\\bmode\\.title\\b")
        val problems = ArrayList<String>()
        sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val path = file.relativeTo(sources).path
            if (path.startsWith("ui/i18n/") || path in PENDING_CORE_NAMES || path.startsWith("analytics/")) return@forEach
            file.readLines().forEachIndexed { i, line ->
                if (pattern.containsMatchIn(line) && "i18n-ignore" !in line && !line.trimStart().startsWith("*") && !line.trimStart().startsWith("//")) {
                    problems += "$path:${i + 1}: ${line.trim()}"
                }
            }
        }
        if (problems.isNotEmpty()) fail("Core English names shown in the app (use CoreText's label()/labelText()):\n" + problems.joinToString("\n"))
    }

    @Test
    fun theCheckCatchesTypicalMistakes() {
        val bad = """
            Text("Cast line")
            Text(if (x) "Finish" else "Next")
            Text("pts", style = s)
            Text(
                "Earned in total: ${'$'}{p.points} pts",
            )
            Icon(GameIcons.Trophy, contentDescription = "Milestones")
            message = "Your progress is backed up."
            SettingsSwitchRow(title = "Vibration", checked = on)
        """.trimIndent()
        assertTrue(findHardcodedText("bad.kt", bad).size >= 7)
        val good = """
            Text(stringResource(R.string.home_cast_button))
            Text("${'$'}{formatPoints(points)} · ${'$'}{p.lanterns}")
            Modifier.testTag("settings_tour")
            store.putString("tour.showAgain", "false")
            GameAnalytics.problem("account_delete", e)
            Text("Deep Line") // i18n-ignore: the game's name in a debug-only label
            val look = BadgeLook(Color.White, "★")
        """.trimIndent()
        assertTrue(findHardcodedText("good.kt", good).joinToString("\n"), findHardcodedText("good.kt", good).isEmpty())
    }

    companion object {
        /**
         * Files converted to string resources, relative to `app/src/main/kotlin/com/deepline/fishing/`.
         * Add a file here once it is fully converted (and keep it here).
         */
        val CONVERTED: List<String> = listOf(
            // The app shell.
            "MainActivity.kt",
            "ui/AppRoot.kt",
            "ui/MainScaffold.kt",
            "ui/Format.kt",
            // The Fish tab and Milestones.
            "ui/HomeScreen.kt",
            "ui/MilestonesScreen.kt",
            // Settings and privacy.
            "ui/settings/SettingsScreen.kt",
            "ui/settings/SoundSettings.kt",
            "ui/settings/NotificationSettings.kt",
            "ui/settings/LanguageSettings.kt",
            "ui/settings/PrivacySettings.kt",
            "ui/settings/Credits.kt",
            "privacy/ConsentSheet.kt",
            "privacy/PrivacyController.kt",
            "privacy/PrivacyChoices.kt",
            // The tour.
            "ui/tour/Tour.kt",
            "ui/tour/SocialTour.kt",
            "ui/tour/TourOverlay.kt",
            // Localisation itself, and text built outside composables.
            "ui/i18n/UiText.kt",
            "ui/i18n/AppLanguage.kt",
            "ui/i18n/CoreText.kt",
            "ui/i18n/SpeciesNames.kt",
            "ui/shop/ShopText.kt",
            "ui/boat/BoatText.kt",
            "ui/daily/DailyText.kt",
            "daily/DailyController.kt",
            "social/SocialController.kt",
            "GameViewModel.kt",
            "AccountClient.kt",
            "OnlineConnection.kt",
            "ui/SkinsScreen.kt",
            // Area A: casting, battles, online and secrets.
            "ui/CastScreen.kt",
            "ui/CastRenderer.kt",
            "ui/BattleScreen.kt",
            "ui/BattleSetupScreen.kt",
            "ui/OnlineLobbyScreen.kt",
            "ui/OnlineText.kt",
            "ui/secrets/FishingSecrets.kt",
            "ui/dex/SecretDex.kt",
            // Area B: the Dex, skins, boat editor, shop, themes, aquarium and the stores.
            "ui/shop/ShopScreen.kt",
            "ui/shop/GiftsUi.kt",
            "ui/shop/SeasonUi.kt",
            "ui/shop/ThemesScreen.kt",
            "ui/shop/AquariumScreen.kt",
            "ui/dex/DexScreen.kt",
            "ui/dex/DexStyle.kt",
            "ui/dex/SpeciesDex.kt",
            "ui/dex/FishDetailScreen.kt",
            "ui/dex/FishPortrait.kt",
            "ui/dex/SkinDex.kt",
            "ui/boat/BoatEditorScreen.kt",
            "billing/Store.kt",
            "billing/TestStore.kt",
            "billing/PlayStore.kt",
            // Area C: the daily loop, reminders and social.
            "ui/daily/DailyUi.kt",
            "daily/Reminders.kt",
            "social/SocialClient.kt",
            "ui/social/SocialUi.kt",
            "ui/social/SocialHubScreen.kt",
            "ui/social/FriendsScreen.kt",
            "ui/social/LeaderboardsScreen.kt",
            "ui/social/TournamentScreen.kt",
            "ui/social/ClubsScreen.kt",
        )

        /**
         * Files that still put a core English name inside an English sentence, until their area is
         * converted (then the sentence becomes a resource and the name an argument or a word of it).
         * Remove a file from here when you convert it.
         */
        val PENDING_CORE_NAMES: Set<String> = setOf(
        )

        /** Calls whose arguments are always text the player reads. */
        private val textCalls = setOf("Text", "SubScreen", "TabScreen", "SettingsSection", "SettingsSwitchRow", "TitleBadge", "Snackbar")

        /** Named parameters that are always text the player reads. */
        private val textParams = setOf("title", "subtitle", "text", "contentDescription", "clickLabel", "label", "message", "body")

        /** Calls whose string arguments are never shown to the player. */
        private val ignoredCalls = setOf(
            "testTag", "Log", "e", "w", "d", "i", "error", "require", "check", "problem", "event", "screen", "setUserProperty",
            "recordError", "getString", "putString", "getSharedPreferences", "rememberInfiniteTransition", "animateFloat",
            "forLanguageTag", "byId", "Suppress", "rememberReminderOptIn", "Exception", "ReportedProblem", "getInstance",
        )

        /** Keys and ids: no spaces, and lower case or with a separator ("tab_fish", "tour.showAgain", "badge:RAINBOW", "pt-BR"). */
        private fun keyLike(text: String): Boolean =
            Regex("[A-Za-z0-9_.:/#\\-]*").matches(text) && (text.none { it.isUpperCase() } || text.any { it in "._:/#-" })

        /** Problems in [source] ("path:line: literal"). */
        fun findHardcodedText(path: String, source: String): List<String> {
            val problems = ArrayList<String>()
            for (literal in literals(source)) {
                val line = source.lineSequence().drop(literal.line - 1).first()
                if ("i18n-ignore" in line) continue
                val text = literal.text
                    .replace(Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%]"), " ")
                    .replace(Regex("\\\\[nt\"'\\\\$]"), " ")
                if (!text.any { it.isLetter() }) continue
                if (literal.calls.any { it in ignoredCalls }) continue
                val inText = literal.calls.any { it in textCalls } || literal.param in textParams
                val wordy = text.any { it.isUpperCase() } || text.trim().contains(' ')
                // A map's key ("Coral Tides" to R.string...) is data from core, not text.
                if (Regex("^\\s*to\\s+R\\.").containsMatchIn(source.substring(literal.end))) continue
                val key = keyLike(text.trim())
                if (inText && !key || wordy && !key || inText && literal.calls.lastOrNull() == "Text") {
                    problems += "$path:${literal.line}: \"${literal.text}\""
                }
            }
            return problems
        }

        private class Literal(val text: String, val line: Int, val calls: List<String>, val param: String?, val end: Int)

        /**
         * The string literals of [source] (template expressions removed), each with the calls it
         * sits in (innermost last) and the named parameter it is given to, if any. A small lexer:
         * comments, char literals, raw strings and nested templates are handled.
         */
        private fun literals(source: String): List<Literal> {
            val out = ArrayList<Literal>()
            // Each open parenthesis: the call it belongs to (the identifier before it), if any.
            val stack = ArrayList<String?>()
            var i = 0
            var line = 1
            fun identBefore(end: Int): String? {
                var j = end - 1
                while (j >= 0 && source[j].isWhitespace()) j--
                val stop = j
                while (j >= 0 && (source[j].isLetterOrDigit() || source[j] == '_')) j--
                return if (stop > j) source.substring(j + 1, stop + 1) else null
            }
            fun paramBefore(start: Int): String? {
                // "name = " right before the literal (a named argument or an assignment).
                val m = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*$").find(source.substring(maxOf(0, start - 80), start)) ?: return null
                return m.groupValues[1]
            }
            fun readString(start: Int, raw: Boolean): Int {
                // Returns the index after the closing quote; records the literal's text.
                val sb = StringBuilder()
                val startLine = line
                var k = start + if (raw) 3 else 1
                while (k < source.length) {
                    val c = source[k]
                    if (raw && source.startsWith("\"\"\"", k)) { k += 3; break }
                    if (!raw && c == '"') { k++; break }
                    if (!raw && c == '\\') { sb.append(c).append(source.getOrElse(k + 1) { ' ' }); k += 2; continue }
                    if (c == '\n') line++
                    if (c == '$' && source.getOrNull(k + 1) == '{') {
                        // Skip a template expression, which can contain strings and braces.
                        var depth = 0
                        k++
                        while (k < source.length) {
                            val d = source[k]
                            if (d == '{') depth++
                            if (d == '}') { depth--; if (depth == 0) { k++; break } }
                            if (d == '"') { k = readString(k, source.startsWith("\"\"\"", k)); continue }
                            if (d == '\n') line++
                            k++
                        }
                        sb.append(' ')
                        continue
                    }
                    if (c == '$' && source.getOrNull(k + 1)?.isLetter() == true) {
                        k++
                        while (k < source.length && (source[k].isLetterOrDigit() || source[k] == '_')) k++
                        sb.append(' ')
                        continue
                    }
                    sb.append(c)
                    k++
                }
                out += Literal(sb.toString(), startLine, stack.filterNotNull(), paramBefore(start), k)
                return k
            }
            while (i < source.length) {
                val c = source[i]
                when {
                    source.startsWith("//", i) -> { while (i < source.length && source[i] != '\n') i++ }
                    source.startsWith("/*", i) -> {
                        val end = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                        line += source.substring(i, end).count { it == '\n' }
                        i = end
                    }
                    c == '\'' -> { i = source.indexOf('\'', i + if (source.getOrNull(i + 1) == '\\') 3 else 2) + 1 }
                    source.startsWith("\"\"\"", i) -> i = readString(i, raw = true)
                    c == '"' -> i = readString(i, raw = false)
                    c == '(' -> { stack += identBefore(i); i++ }
                    c == ')' -> { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex); i++ }
                    c == '\n' -> { line++; i++ }
                    else -> i++
                }
            }
            return out
        }
    }
}
