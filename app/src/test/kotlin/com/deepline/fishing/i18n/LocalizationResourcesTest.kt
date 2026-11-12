package com.deepline.fishing.i18n

import com.deepline.fishing.ui.i18n.AppLanguage
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The rules every string resource file is held to, English and translations alike (see
 * docs/localization.md). A plain JVM test: it reads `src/main/res` directly.
 *
 * - English (`values/`): one `strings_<area>.xml` file per area, each starting with a comment that
 *   says what the area is; no empty strings; plurals have `one` and `other`.
 * - A translation (`values-fr/`...) mirrors English file by file: a file that exists has every key
 *   of its English file (and no other), the same placeholders (positions and types), the plural
 *   quantities its language needs, and no `translatable="false"` string.
 * - Everywhere: well-formed XML, apostrophes escaped (\'), no unescaped leading @ or ?.
 * - Languages in [REQUIRED_LANGUAGES] must have every English file. Translators add their
 *   language there when they finish, which makes the check strict for it (see the guide).
 */
class LocalizationResourcesTest {
    private val res = File("src/main/res")

    /** One resource file, parsed: string values and plural items by key. */
    private class Strings(val file: File) {
        val strings = LinkedHashMap<String, String>()
        val plurals = LinkedHashMap<String, Map<String, String>>()
        val untranslatable = HashSet<String>()
        var startsWithComment = false
        val keys: Set<String> get() = strings.keys + plurals.keys.map { "plurals/$it" }
    }

    private fun parse(file: File): Strings {
        val doc = try {
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)
        } catch (e: Exception) {
            fail("${file.path} isn't well-formed XML: ${e.message}")
            error("unreachable")
        }
        val out = Strings(file)
        out.startsWithComment = generateSequence(doc.firstChild) { it.nextSibling }
            .firstOrNull { it.nodeType != Node.TEXT_NODE }?.nodeType == Node.COMMENT_NODE
        val root = doc.documentElement
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> {
                    if (name in out.strings) fail("${file.path}: '$name' is defined twice")
                    out.strings[name] = node.textContent
                    if (node.getAttribute("translatable") == "false") out.untranslatable += name
                }
                "plurals" -> {
                    val items = LinkedHashMap<String, String>()
                    val list = node.getElementsByTagName("item")
                    for (j in 0 until list.length) {
                        val item = list.item(j) as Element
                        items[item.getAttribute("quantity")] = item.textContent
                    }
                    out.plurals[name] = items
                }
                "string-array" -> fail("${file.path}: use one string per entry, not a string-array ('$name')")
            }
        }
        return out
    }

    private fun stringFiles(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.name.startsWith("strings") && f.name.endsWith(".xml") }?.sortedBy { it.name }.orEmpty()

    private val english: Map<String, Strings> by lazy { stringFiles(File(res, "values")).associate { it.name to parse(it) } }

    /** Translation folders that hold string files (values-fr, values-pt-rBR...), by folder name. */
    private val translations: Map<String, Map<String, Strings>> by lazy {
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
            .filter { stringFiles(it).isNotEmpty() }
            .associate { dir -> dir.name to stringFiles(dir).associate { it.name to parse(it) } }
    }

    @Test
    fun englishFilesFollowTheConventions() {
        val problems = ArrayList<String>()
        assertTrue("No string files in values/", english.isNotEmpty())
        for ((name, file) in english) {
            if (!Regex("strings_[a-z0-9_]+\\.xml").matches(name)) problems += "values/$name: name string files strings_<area>.xml"
            if (!file.startsWithComment) problems += "values/$name: start the file with a comment saying what the area is, for translators"
            val prefix = name.removePrefix("strings_").removeSuffix(".xml")
            for (key in file.keys) {
                val bare = key.removePrefix("plurals/")
                if (!Regex("[a-z][a-z0-9_]*").matches(bare)) problems += "values/$name: '$bare' isn't a lower_snake_case key"
            }
            for ((key, items) in file.plurals) {
                for (q in listOf("one", "other")) if (q !in items) problems += "values/$name: plurals '$key' needs '$q'"
            }
            if (file.keys.isEmpty()) problems += "values/$name: empty file"
            // Keys say where they're used: area_screen_purpose (common_, shell_ and core_ are shared).
            if (prefix !in setOf("common", "shell", "core", "species")) {
                val foreign = file.keys.map { it.removePrefix("plurals/") }.filterNot { k -> keyPrefixes(prefix).any { k.startsWith(it) } }
                if (foreign.isNotEmpty()) problems += "values/$name: keys should start with ${keyPrefixes(prefix).joinToString(" or ")}: $foreign"
            }
        }
        // All of English lives in the area files (no strings.xml or other file with <string>s).
        File(res, "values").listFiles { f -> f.name.endsWith(".xml") && !f.name.startsWith("strings_") }.orEmpty().forEach { f ->
            if (f.readText().contains("<string ") || f.readText().contains("<plurals ")) problems += "values/${f.name}: put strings in a strings_<area>.xml file"
        }
        // A key is defined once across all files.
        val seen = HashMap<String, String>()
        for ((name, file) in english) for (key in file.keys) {
            seen.put(key, name)?.let { problems += "'$key' is in both $it and $name" }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    /** strings_battle.xml holds battle_ and online_ keys; the others hold their area's prefix. */
    private fun keyPrefixes(area: String): List<String> = when (area) {
        "battle" -> listOf("battle_", "online_")
        else -> listOf(area + "_")
    }

    @Test
    fun everyFileIsWellFormedAndEscaped() {
        val problems = ArrayList<String>()
        val all = english.values + translations.values.flatMap { it.values }
        for (file in all) {
            val where = file.file.parentFile.name + "/" + file.file.name
            fun check(key: String, value: String) {
                if (value.isBlank()) problems += "$where: '$key' is empty"
                val quoted = value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")
                if (!quoted && Regex("(?<!\\\\)'").containsMatchIn(value)) problems += "$where: '$key' has an apostrophe without a backslash (write \\')"
                if (value.startsWith("@") || value.startsWith("?")) problems += "$where: '$key' starts with ${value[0]}: escape it with a backslash"
                // "%1$" with no type after it, or two "%s" without positions (translators must be able to reorder).
                if (Regex("%\\d+\\$(?![-#+ 0,(]*\\d*(\\.\\d+)?[sdfxXcboegEG])").containsMatchIn(value)) problems += "$where: '$key' has a broken placeholder"
                val unnumbered = Regex("%(?!\\d+\\$)[-#+ 0,(]*\\d*(\\.\\d+)?[sdfxXcboegEG]").findAll(value.replace("%%", "")).count()
                if (unnumbered > 1) problems += "$where: '$key' has several placeholders without positions: write %1\$s, %2\$d..."
            }
            file.strings.forEach { (k, v) -> check(k, v) }
            file.plurals.forEach { (k, items) -> items.forEach { (q, v) -> check("$k[$q]", v) } }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test
    fun translationsMatchEnglish() {
        val problems = ArrayList<String>()
        for ((folder, files) in translations) {
            val language = folder.removePrefix("values-")
            val quantities = PLURAL_QUANTITIES[language.substringBefore("-r")]
            for ((name, file) in files) {
                val source = english[name]
                if (source == null) {
                    problems += "$folder/$name has no English file values/$name"
                    continue
                }
                val missing = source.keys - source.untranslatable.toSet() - file.keys
                if (missing.isNotEmpty()) problems += "$folder/$name misses ${missing.size} key(s): ${missing.take(12)}"
                val extra = file.keys - source.keys
                if (extra.isNotEmpty()) problems += "$folder/$name has key(s) English doesn't: $extra"
                for (key in file.untranslatable + (file.strings.keys intersect source.untranslatable)) {
                    problems += "$folder/$name: '$key' isn't translatable: remove it"
                }
                for ((key, value) in file.strings) {
                    val en = source.strings[key] ?: continue
                    if (formatArgs(value) != formatArgs(en)) {
                        problems += "$folder/$name: '$key' placeholders ${formatArgs(value)} don't match English ${formatArgs(en)}"
                    }
                }
                for ((key, items) in file.plurals) {
                    val en = source.plurals[key] ?: continue
                    val enArgs = formatArgs(en["other"] ?: en.values.first())
                    if (quantities != null) {
                        val need = quantities - items.keys
                        if (need.isNotEmpty()) problems += "$folder/$name: plurals '$key' needs $need for this language"
                        val unused = items.keys - quantities
                        if (unused.isNotEmpty()) problems += "$folder/$name: plurals '$key' has $unused, which this language never uses"
                    }
                    for ((q, v) in items) {
                        val args = formatArgs(v)
                        // Every placeholder must be one English has; only `other` must have them all.
                        if (!enArgs.containsAll(args) || (q == "other" && args != enArgs)) {
                            problems += "$folder/$name: plurals '$key' [$q] placeholders $args don't match English $enArgs"
                        }
                    }
                }
            }
            if (language in REQUIRED_LANGUAGES) {
                // A file with nothing to translate (e.g. the species names, kept in English) isn't needed.
                val translatable = english.filterValues { it.keys.size > it.untranslatable.size }.keys
                val absent = translatable - files.keys
                if (absent.isNotEmpty()) problems += "$folder is required but has no $absent"
            }
        }
        for (language in REQUIRED_LANGUAGES) {
            if ("values-$language" !in translations) problems += "values-$language is required but missing"
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test
    fun theLocaleConfigListsExactlyTheGamesLanguages() {
        val config = File(res, "xml/locales_config.xml").readText()
        val listed = Regex("android:name=\"([^\"]+)\"").findAll(config).map { it.groupValues[1] }.toList()
        assertTrue(
            "res/xml/locales_config.xml lists $listed, Settings > Language offers ${AppLanguage.supported.map { it.tag }}",
            listed == AppLanguage.supported.map { it.tag },
        )
        // Only these languages ship (library strings in other languages are filtered out).
        val gradle = File("build.gradle.kts").readText()
        for (language in AppLanguage.supported) {
            val filter = if (language.tag == "en") "en" else folderOf(language.tag!!).removePrefix("values-")
            assertTrue("app/build.gradle.kts localeFilters misses \"$filter\"", gradle.contains("\"$filter\""))
        }
        // Translators fill exactly the folders of these languages.
        for (folder in translations.keys) {
            val known = AppLanguage.supported.map { folderOf(it.tag!!) }
            assertTrue("$folder isn't one of the game's languages $known", folder in known)
        }
    }

    companion object {
        /**
         * Languages that must have every English file (every file with something to translate):
         * all five translations are complete, so a missing file or key fails the build. A new
         * language goes here once it is complete.
         */
        val REQUIRED_LANGUAGES: Set<String> = setOf("fr", "es", "de", "pt-rBR", "tr")

        /**
         * The plural categories each language uses for whole numbers (Unicode CLDR, as in Android's
         * ICU): French, Spanish and Portuguese also have `many` (exact millions: "1 000 000 de...").
         */
        val PLURAL_QUANTITIES: Map<String, Set<String>> = mapOf(
            "en" to setOf("one", "other"),
            "fr" to setOf("one", "many", "other"),
            "es" to setOf("one", "many", "other"),
            "pt" to setOf("one", "many", "other"),
            "de" to setOf("one", "other"),
            "tr" to setOf("one", "other"),
        )

        /** "pt-BR" -> "values-pt-rBR", "fr" -> "values-fr", "en" -> "values" (the default). */
        fun folderOf(tag: String): String = when {
            tag == "en" -> "values"
            '-' in tag -> "values-" + tag.substringBefore('-') + "-r" + tag.substringAfter('-')
            else -> "values-$tag"
        }

        /**
         * A string's placeholders as (position, type): "%1$s" -> 1:s. A lone "%d" counts as
         * position 1 (plurals use it). "%%" is a literal percent sign.
         */
        fun formatArgs(value: String): List<String> {
            val found = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?([sdfxXcboegEG])").findAll(value.replace("%%", ""))
            var next = 1
            return found.map { m ->
                val position = m.groupValues[1].removeSuffix("$").toIntOrNull() ?: next++
                val type = m.groupValues[3].let { if (it == "S") "s" else it }
                "$position:$type"
            }.toList().distinct().sorted()
        }
    }
}
