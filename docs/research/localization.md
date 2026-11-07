# Localisation: research and decisions

Deep Line was English only, with every piece of UI text typed into Kotlin. This note gathers the
current official guidance on localising an Android app (Kotlin, Jetpack Compose, minSdk 24,
targetSdk 36) and records the decisions taken for the game. The working guide for people
extracting and translating text is [`docs/localization.md`](../localization.md).

Target languages: English (source), French, Spanish, German, Brazilian Portuguese and Turkish.

Sources were read in November 2026. `developer.android.com` pages were read in full; the Unicode
CLDR site, Google Play Help and material.io could not be reached from the build machine, so the
points taken from them are from their published pages as linked (search results and prior
knowledge), and marked as such.

## 1. String resources

- **Everything the player reads goes in `res/values/strings*.xml`**, never in code: "don't hardcode
  any strings. Instead, declare all your strings as resources in a default strings.xml file"
  ([Localize your app][loc]). The file name is free: "The filename is arbitrary. The `<string>`
  element's name is used as the resource ID" ([String resources][str]). We split English into
  one file per area (`strings_common.xml`, `strings_home.xml`, `strings_core.xml`...), so areas can
  be extracted and translated in parallel without conflicts.
- **The default resources must be complete**: "If this default file is absent, or if it's missing
  a string that your app needs, then your app doesn't run" ([Localize your app][loc]). A
  translation may be partial (missing strings fall back to English); English may not.
- **Escaping**: apostrophes and quotes must be escaped (`\'`, `\"`), a leading `@` or `?` too,
  and whitespace runs collapse unless quoted ([String resources][str]). `aapt2` rejects an
  unescaped apostrophe, so the quality test checks it before the build does.
- **Positional arguments**: `%1$s`, `%2$d` ([String resources][str], "Formatting strings"), so
  translators can reorder them. Compose: `stringResource(R.string.x, a, b)`; elsewhere
  `context.getString(R.string.x, a, b)` ([Resources in Compose][compose]). `getString` with
  arguments formats with the resources' own locale, so digits follow the language.
- **Translator context**: "make sure to describe the context in which the string is used ...
  where is this in the UI?" with an XML comment before the string ([Localize your app][loc]).
  Every area file starts with a comment on what the area is, and strings with arguments say what
  each argument is.
- **What not to translate**: mark it with `translatable="false"` (a whole string) or `<xliff:g>`
  (part of one: a code, a placeholder, a name) ([Localize your app][loc]). We use
  `translatable="false"` for the app name, and comments for embedded names (CC0,
  OpenGameArt.org, Google Firebase, "DL-" in recovery codes).
- **Styled text**: formatting functions strip markup; Compose builds `AnnotatedString`s, or
  `<annotation>` tags for styled translated text ([String resources][str]). The game uses plain
  strings only.

## 2. Plurals (quantity strings)

- Android's `<plurals>` supports `zero`, `one`, `two`, `few`, `many`, `other`, chosen "solely
  based on grammatical necessity": in English `zero` is ignored even for 0 ([String
  resources][str]). Always supply `one` and `other`, and "always include %d in one": a
  language's `one` may cover numbers other than 1 (French and Portuguese: 0 and 1).
- "If your message doesn't contain the quantity number, it is probably not a good candidate for a
  plural" ([String resources][str]): "once"/"twice" style wording is avoided; quantity-neutral
  phrasings ("Books: 1") are fine.
- Compose: `pluralStringResource(R.plurals.x, count, count)`: the count is passed twice, once to
  choose the form and once as the `%d` argument ([Resources in Compose][compose]).
- Categories per language (Unicode CLDR plural rules, [language plural rules][cldr]; CLDR 38+
  added `many` for French, and later Spanish and Portuguese, for exact millions):

  | Language | Categories (whole numbers) | Notes |
  | --- | --- | --- |
  | English | one, other | one = 1 |
  | German | one, other | one = 1 |
  | Turkish | one, other | one = 1. Turkish nouns stay singular after a number ("3 balık"), so `one` and `other` are often the same text, but both are required. |
  | French | one, many, other | one = 0 and 1; many = exact millions ("1 000 000 de poissons") |
  | Spanish | one, many, other | one = 1; many = exact millions ("1 000 000 de peces") |
  | Portuguese (Brazil) | one, many, other | one = 0 and 1 (pt-BR follows "pt"); many = exact millions |

  The quality test requires exactly these per language. Android lint also asks for `many` in
  French, Spanish and Portuguese; a missing form falls back to `other` at run time.

## 3. Per-app language

[App languages][applang] (Android 13+):

- Users can pick a language per app in the system settings (Settings > Apps > app > Language), if
  the app declares its languages with `android:localeConfig`. Apps can set and read it with
  `LocaleManager.setApplicationLocales` / `getApplicationLocales`; "these APIs automatically
  sync with system settings", and setting it "triggers a configuration change" that recreates
  the activity, so Compose recomposes with the new resources.
- **Locale config**: either generated (`androidResources { generateLocaleConfig = true }` with
  `res/resources.properties` `unqualifiedResLocale=en-US`, AGP 8.1+; it lists the `values-*`
  folders of the app and its library modules) or a hand-written `res/xml/locale_config.xml`
  referenced from the manifest. The two can't be combined.
- **Back-compat (Android 12 and lower)**: AndroidX `AppCompatDelegate.setApplicationLocales` (AppCompat
  1.6+) works for all versions, but "the backward compatible APIs work with the AppCompatActivity
  context" and, with Compose, "you must extend your activity from AppCompatActivity. Otherwise,
  setting the app locale won't work"; storage on old versions needs the `autoStoreLocales`
  metadata on `AppLocalesMetadataHolderService`. Google "strongly recommend[s]" AppCompat but
  documents the framework API as an alternative, and asks apps with their own storage to hand
  the stored choice to the system once, on the first run on Android 13.
- **Keep library strings in step**: `resConfigs` / locale filters make the build keep only the
  app's languages, "preventing translated strings from being included from other libraries that
  might support languages that your app does not support" ([App languages][applang],
  [Multilingual support][multi]).
- Resolution since Android 7: a missing exact locale falls back to the parent ("fr-CH" -> "fr")
  and then to siblings ("fr-FR"), so store strings in the most common parent (`values-fr`, not
  `values-fr-rFR`) ([Multilingual support][multi]). Brazilian Portuguese is `values-pt-rBR`; a
  phone set to European Portuguese finds it as the closest sibling.

**Decision: framework `LocaleManager` on Android 13+, our own preference below, no AppCompat.**
`MainActivity` is a `ComponentActivity` with a platform theme; adopting AppCompat means a new
dependency, `AppCompatActivity` and an AppCompat theme for one feature. Instead
(`ui/i18n/AppLanguage.kt`):

- Android 13+: the in-app picker calls `LocaleManager.setApplicationLocales` (empty list = the
  phone's language). The system is the source of truth, so a choice made in the system settings
  shows in the game and vice versa, and the system recreates the activity.
- Android 12 and lower: the choice is a device preference (`app.language` in the app's
  SharedPreferences, like the sound settings, so it isn't in the save or sent anywhere);
  `MainActivity.attachBaseContext` wraps the context in that locale and the picker recreates
  the activity. `Locale.setDefault` follows, so number formatting matches.
- Upgrading to Android 13 hands a stored choice to the system once
  (`AppLanguages.handOverToSystem`), as the guide recommends.
- Locale config: **hand-written** `res/xml/locales_config.xml` listing exactly en, fr, es, de,
  pt-BR, tr, because the generated one would list only the folders that exist (English alone until
  the translations land) and could pick up any stray folder. A test keeps it equal to the picker's
  list (`AppLanguage.supported`) and to the Gradle locale filters
  (`androidResources.localeFilters`), which also keep library strings to these languages.
- Text built outside the activity (reminder notifications from a WorkManager worker) uses
  `AppLanguages.localized(context)`: on 13+ the application context already has the app locale;
  below it is wrapped from the preference.

## 4. Pseudo-locales

[Pseudolocales][pseudo]: **English (XA)** "adds Latin accents to the base English UI text, expands
the original text by adding non-accented text, and brackets each message unit", exposing
hard-coded text (it stays plain), truncation and concatenation ("one message split across two or
more brackets"); **AR (XB)** flips the text direction to test RTL. Enabled per build type with
`isPseudoLocalesEnabled = true`.

**Decision**: on in debug builds. Beyond the phone's developer languages, debug builds list both
pseudo-locales in Settings > Language, so a tester can switch in two taps; a UI test switches to
English (XA) and checks that the Settings screen is pseudo-localized. The locale filters must
include `en-rXA` and `ar-rXB` or the build drops them.

## 5. Text expansion and layout

- Translations run longer than English, most of all for short strings. The W3C's summary of
  IBM's guidelines gives up to 200-300% for strings of up to 10 characters, falling to about 30%
  for paragraphs ([Text size in translation][w3c]); design systems quote German about 30% and
  French 15-20% longer for typical UI text (e.g. "Share" -> "Freigeben" 1.8×, "Partager" 1.5×,
  [Lightning Design System][slds]).
- **Decisions**: buttons and chips must allow wrapping or at least 1.5× their English width; the
  bottom tabs are one short word each (comment asks for ~10 characters); the Fish tab's title is
  the brand name; counters and units are format strings, not concatenations. Area agents check
  their screens with the English (XA) pseudo-locale and in German and French once translated (the
  UI tests take screenshots).

## 6. Numbers, dates and units

- Use formatters, never hard-coded formats: since Android 7 "there can be significant differences
  in formats for dates, times, currencies ... make sure to use formatters instead of hard coding"
  ([Multilingual support][multi]); in Compose the current locale is
  `LocalConfiguration.current.locales[0]` ([Localize your app][loc]).
- **Decisions** (`ui/Format.kt`, `UiText.Number`/`Decimal`): grouped integers with
  `NumberFormat.getIntegerInstance(locale)` ("12,500", "12 500", "12.500"), decimals with
  `NumberFormat.getNumberInstance(locale)` ("1.5", "1,5"), euro prices with a currency formatter
  ("€0.99", "0,99 €"; Google Play's own formatted price wins when present). Units ("%1$s m",
  "%1$s kg", "%1$s pts"), short durations ("2 d 5 h") and the compact "5k" are resources so each
  language picks its symbol and spacing. Clock times on the hour are a resource ("%1$02d:00").
  Kotlin's `lowercase()`/`uppercase()` are locale-independent, which avoids the Turkish dotted
  and dotless i problem in keys; display text is never case-changed in code (German capitalises
  nouns), translators write each form.

## 7. Building sentences

- Don't concatenate pieces of sentences: translators can't reorder them, and the pseudo-locale shows
  them as separate brackets ([Pseudolocales][pseudo]). One resource per sentence, with arguments.
- Names inserted into sentences break grammar in inflected languages (German cases, Turkish
  suffixes, French gender agreement). **Decision**: insert a name only where no inflection is
  needed (after a colon, as a label, in parentheses), or write the sentence with the name in it.
  The tour therefore names tackle, zones and modes in its own sentences (the glossary keeps them
  equal to the labels), and `TourContentTest` checks the English tour still uses the labels' words.
- Lists ("a, b and c") come from `common_list_middle`/`common_list_end`, so the conjunction is
  translated ("et", "y", "und", "e", "ve").

## 8. What is not translated

- **Analytics names** (events, parameters, user properties) are an API, not text: they stay in
  English (and documented in `docs/analytics-events.md`). Same for preference keys, test tags,
  store product ids, server paths and protocol fields.
- **Player-typed names** (the online name, friends' names) and **ids/codes** (player ids, room
  codes, recovery codes) are shown as typed.
- **Generated club names and strangers' public names** (`Clubs.name`, `Leaderboards.anglerName`)
  stay English: every member of a club sees the same name whatever their language, and public
  names are an identity derived from the id.
- **Server messages**: the battle server sends English error text (`ServerMessage.Error`); until
  the online area maps the known ones to resources, they show as sent.
- **Credits**: titles and authors of CC0 works keep their original names.

## 9. Proper nouns and species names

Game localisers keep a glossary of names and decide per name whether it is kept, translated or
adapted, so a name never changes between screens ([Alconost on characters][alconost],
[Allcorrect on glossaries][allcorrect]); creature names in collection games are commonly
translated or transcreated per language (e.g. Pokémon's French names, discussed in the localisation
literature, e.g. an article in [Digital Translation][benjamins]), because players read them as words, not
labels.

**Decision: species names are translated, through resources keyed by species id.**

- 200 species: 16 hand-made, 183 generated (adjective + kind of fish from fixed English word
  lists and a fixed seed) and the Deep Sovereign. Their ids are saved in players' logs and must
  never change, so neither the ids nor the generator change. Only the display name is looked up:
  `FishSpecies.nameText()` -> `strings_species.xml` (`species_<id>`, generated once from core,
  with an explicit id -> resource map in `SpeciesNames.kt`, no reflection).
- English values equal core's names (`CoreTextTest`), so English never drifts; a missing
  translation falls back to English.
- Translators translate each adjective and fish word the same way everywhere (the guide lists the
  generator's words), in their language's word order and agreement ("Gobie tacheté"), and keep
  names unique (checked).
- Names are labels: sentences quote them after a colon ("Catch a fish of this species: Sun
  Bream.") so they never need inflecting.

The same mechanism covers everything else core names in English (zones, rope stats, tackle,
modes, skins, boat options, shop items, themes, seasons, boards, tournament tiers, secret titles):
core keeps its English names for the server and tests; the app maps each enum or id to a resource
(`ui/i18n/CoreText.kt`), with exhaustive `when`s so a new enum entry doesn't compile without a
string, and `CoreTextTest` checks every id-keyed entry (shop items, boats, species, seasons).

## 10. Text built outside composables

View models and controllers build messages (purchase results, notices during a cast, social and
daily messages), and the tour builds its steps in an object. **Decision**: a small sealed type,
`UiText` (`ui/i18n/UiText.kt`): a resource id with arguments (which may themselves be `UiText`s),
a plural, a raw string (names, codes), formatted numbers, and joins/lists. State holds `UiText`;
composables resolve it with `asString()`, so a message on screen follows a language change; tests
resolve it with an Android context. Where text must become data (battle player names stored in
core's `Battle`, a notification), it is resolved once with `AppLanguages.localized(context)`.

## 11. Quality checks

- `LocalizationResourcesTest` (JVM): well-formed XML; English in `strings_<area>.xml` files with
  a header comment and area-prefixed keys; every translation file mirrors its English file (no
  missing or extra keys, same placeholders by position and type, the plural categories of its
  language, no non-translatable string); apostrophes escaped; nothing empty; the locale config,
  the picker and the Gradle filters agree. `REQUIRED_LANGUAGES` becomes strict per language when
  its translation is complete.
- `HardcodedTextTest` (JVM): no literal text in files already converted (a list the area agents
  extend), and no core English name shown anywhere (with a shrinking list of pending files).
- `CoreTextTest`, `UiTextTest`, `TourContentTest` (Robolectric, English), `LanguageUiTest`
  (Android 16: picker, system setting, recreation, pseudo-locale) and `LanguageLegacyUiTest`
  (Android 12: the game's own preference).

## Sources

- [loc]: Android Developers, *Localize your app*: https://developer.android.com/guide/topics/resources/localization
- [str]: Android Developers, *String resources* (plurals, formatting, escaping): https://developer.android.com/guide/topics/resources/string-resource
- [compose]: Android Developers, *Resources in Compose*: https://developer.android.com/develop/ui/compose/resources
- [applang]: Android Developers, *Per-app language preferences*: https://developer.android.com/guide/topics/resources/app-languages
- [multi]: Android Developers, *Language and locale resolution overview*: https://developer.android.com/guide/topics/resources/multilingual-support
- [pseudo]: Android Developers, *Test your app with pseudolocales*: https://developer.android.com/guide/topics/resources/pseudolocales
- [cldr]: Unicode CLDR, *Language Plural Rules* (not reachable from the build machine; rules as published): https://www.unicode.org/cldr/charts/49/supplemental/language_plural_rules.html
- [w3c]: W3C Internationalization, *Text size in translation*: https://www.w3.org/International/articles/article-text-size
- [slds]: Salesforce Lightning Design System, *Localization guidelines* (expansion examples): https://winter-21.lightningdesignsystem.com/guidelines/localization
- [alconost]: Alconost, *How developers localize game characters*: https://alconost.com/en/blog/game-characters-localization
- [allcorrect]: Allcorrect, *Localization glossaries: what they are and why they matter*: https://allcorrectgames.com/insights/localization-glossaries-what-they-are-and-why-they-matter
- [benjamins]: *Digital Translation* (John Benjamins), article on the French localisation of Pokémon names: https://benjamins.com/catalog/dt.26024.rou
- Google Play Console Help, *Translate and localize your app* (checklist; not reachable from the build machine): https://support.google.com/googleplay/android-developer/answer/9844778
- Material Design 3, *UX writing best practices* (not reachable from the build machine): https://m3.material.io/foundations/content-design/style-guide/ux-writing-best-practices

[loc]: https://developer.android.com/guide/topics/resources/localization
[str]: https://developer.android.com/guide/topics/resources/string-resource
[compose]: https://developer.android.com/develop/ui/compose/resources
[applang]: https://developer.android.com/guide/topics/resources/app-languages
[multi]: https://developer.android.com/guide/topics/resources/multilingual-support
[pseudo]: https://developer.android.com/guide/topics/resources/pseudolocales
[cldr]: https://www.unicode.org/cldr/charts/49/supplemental/language_plural_rules.html
[w3c]: https://www.w3.org/International/articles/article-text-size
[slds]: https://winter-21.lightningdesignsystem.com/guidelines/localization
[alconost]: https://alconost.com/en/blog/game-characters-localization
[allcorrect]: https://allcorrectgames.com/insights/localization-glossaries-what-they-are-and-why-they-matter
[benjamins]: https://benjamins.com/catalog/dt.26024.rou
