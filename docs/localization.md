# Localisation guide

How Deep Line's text is localised: for whoever converts a screen's text to resources, and for the
translators. The research and the reasons behind each choice are in
[`docs/research/localization.md`](research/localization.md).

Languages: **English** (source, `res/values/`), **French** (`values-fr`), **Spanish** (`values-es`),
**German** (`values-de`), **Brazilian Portuguese** (`values-pt-rBR`) and **Turkish** (`values-tr`).
Players pick one in Settings > Language (or keep the phone's language); on Android 13+ it is the
same setting as the system's per-app language.

## 1. The rules in one screen

1. Every word the player reads is a string resource. No text in Kotlin, not even "OK".
2. English lives in `app/src/main/res/values/strings_<area>.xml`, one file per area, each starting
   with a comment saying what the area is. Keys are `<area>_<screen>_<purpose>`, lower_snake_case.
3. Comment every string a translator could get wrong: where it shows, what each `%1$s` is, how
   short it must be.
4. Arguments are positional (`%1$s`, `%2$d`). Never build a sentence from pieces.
5. A number in a sentence about a quantity uses `<plurals>`.
6. Text built outside a composable is a `UiText`, resolved where it is shown.
7. Names of things core defines (zones, tackle, modes, skins, species, shop items...) come from
   `ui/i18n/CoreText.kt` (`zone.label()`), never from core's `displayName`.
8. Numbers, prices, units and times go through `ui/Format.kt` / `UiText.number`, never `"$n pts"`.
9. Don't translate analytics names, keys, ids, codes, or names players type.
10. Run the quality tests; add your converted files to `HardcodedTextTest.CONVERTED`.

## 2. Files and keys

| File | Keys | What |
| --- | --- | --- |
| `strings_common.xml` | `common_` | Buttons (OK, Cancel, Back), units (m, kg, pts), durations, numbers, lists, plurals used everywhere (days, Lanterns). **Reuse these** instead of copying a word into your file. |
| `strings_shell.xml` | `shell_` | Tabs, Shop pages, Dex sections, dialogs over every screen (AppRoot). |
| `strings_core.xml` | `core_` | Names and descriptions of what core defines (see section 6). |
| `strings_species.xml` | `species_` | The 200 species names, by id. |
| `strings_home.xml` | `home_` | The Fish tab. |
| `strings_milestones.xml` | `milestones_` | Milestones. |
| `strings_settings.xml` | `settings_` | Settings and its sections (sound, notifications, language, credits). |
| `strings_privacy.xml` | `privacy_` | Consent sheet, Settings > Privacy, account deletion. |
| `strings_tour.xml` | `tour_` | The guided tour (`tour_<step id>_title` / `_body`). |
| `strings_shop.xml` | `shop_` | Shop messages, what items give, account errors; the Shop screens go here. |
| `strings_boat.xml` | `boat_` | Boat editor. |
| `strings_cast.xml` | `cast_` | Notices during a cast; the cast screen, HUD and results go here. |
| `strings_battle.xml` | `battle_`, `online_` | Battles, the online lobby and online errors. |
| `strings_daily.xml` | `daily_` | Daily rewards, challenges, migrations, reminders. |
| `strings_social.xml` | `social_` | Friends, leaderboards, tournament, clubs. |

New areas get a new file (`strings_dex.xml` with `dex_`, `strings_secrets.xml` with `secrets_`...).
`LocalizationResourcesTest` checks that a file's keys start with its area (`strings_battle.xml`
also takes `online_`), that no key is defined twice, and that nothing is left in another file.

Write XML values carefully:

- Escape apostrophes and quotes: `don\'t`, `\"`. (French and Turkish use many apostrophes:
  `l\'eau`, `Deep Line\'ı`.)
- A literal `%` in a string that has arguments is `%%`.
- Line breaks are `\n`; whitespace runs collapse unless the value is in double quotes.
- A value starting with `@` or `?` needs a backslash.

A string with context:

```xml
<!-- A fish was caught. %1$s: the species; %2$s: points won, already formatted. -->
<string name="cast_notice_caught">"%1$s  +%2$s"</string>
```

## 3. Arguments and sentences

- Pass numbers that the player reads with grouping as **formatted strings** (`%1$s` with
  `UiText.number(n)` or `formatPoints(n)`); pass small counts, depths and levels as `%1$d`.
- Pass decimals formatted (`UiText.decimal(1.5)` -> "1.5" / "1,5").
- **A name inside a sentence must not need inflecting.** French agreement, German cases and
  Turkish suffixes change words around a name, so put names where no grammar touches them: after a
  colon ("Too heavy: %1$s"), in parentheses ("Rival (%1$s)"), or as a label on its own. When a
  sentence really needs the name in it, write one string per case, or write the name into the
  sentence (as the tour does) and keep it equal to the label (see the glossary).
- Lists: `UiText.list(parts)` gives "a, b and c" with the language's own conjunction
  (`common_list_middle`, `common_list_end`). `UiText.join(parts, ", ")` for plain comma lists.
- Never concatenate translated pieces (`stringResource(a) + " " + stringResource(b)`), except
  with separators that need no translation (" · ", line breaks).
- Never change case in code (`.lowercase()`, `.uppercase()`, `replaceFirstChar`): German
  capitalises nouns and Turkish has dotted and dotless i. Write each form as its own string.

## 4. Plurals

Use `<plurals>` when the sentence contains the count. Give every form a `%d` (or the count as an
argument), since `one` isn't always 1:

```xml
<plurals name="common_days">
    <item quantity="one">%d day</item>
    <item quantity="other">%d days</item>
</plurals>
```

```kotlin
pluralStringResource(R.plurals.common_days, n, n)   // in a composable: the count twice
UiText.plural(R.plurals.common_days, n)             // anywhere: the count is the argument
UiText.plural(R.plurals.social_challenge_sent, minutes, name, minutes) // more arguments
```

The forms each language must have (the test checks exactly these):

| Language | Forms |
| --- | --- |
| English, German, Turkish | `one`, `other` |
| French, Spanish, Brazilian Portuguese | `one`, `many`, `other` (`many` is for exact millions; it can repeat `other`) |

French and Portuguese use `one` for 0 and 1. Turkish keeps the noun singular after a number, so
its `one` and `other` are often identical: write both anyway. Don't use plurals for "once/twice"
or wording without the number; prefer quantity-neutral phrasing ("Swaps a day: %1$d").

## 5. Text built outside composables: `UiText`

View models, controllers and the tour build text before anything is drawn. They return or hold
a `UiText` (`ui/i18n/UiText.kt`), and the screen resolves it:

```kotlin
// GameViewModel
shopMessage = UiText.res(R.string.shop_message_thanks, item.nameText(), ShopText.contents(item, progress))
// AppRoot
Text(message.asString())
```

- `UiText.res(id, args...)`, `UiText.plural(id, count, args...)`: arguments can be numbers,
  strings, or other `UiText`s (resolved first).
- `UiText.raw(text)`: final text that is never translated (a player's name, a code, a server's
  own words).
- `UiText.number(n)`, `UiText.decimal(x)`: numbers in the language's format.
- `UiText.join(parts, separator)`, `UiText.lines(parts)`, `UiText.list(parts)`.
- In a composable: `text.asString()`. With a context: `text.resolve(context)`. In tests:
  `text.text` and `str(R.string.x)` (`I18nTestText.kt`, Robolectric).
- **Watch out for string templates**: `"$message"` with a `UiText` compiles and prints the object.
  Resolve first (`${message.asString()}`) or, better, make the whole sentence a resource.
- Text that must become data (battle player names in core's `Battle`) or leave the activity (a
  notification from a WorkManager worker) is resolved once with
  `AppLanguages.localized(context)`: on Android 12 and lower only that context has the game's
  language. Don't keep resolved strings in state that outlives a language change otherwise.
- Drawing on a canvas (`DrawScope`, e.g. `CastRenderer.drawLabel`): resolve the strings in the
  composable (`stringResource`) and pass them to the draw function.
- Exceptions shown to the player carry a `UiText` (`AccountClient.AccountException.text`); their
  `message` stays technical (for crash reports, never player text).

## 6. Names from core: `CoreText`

Core keeps English names (`displayName`, `title`, `summary`, `name`, `tagline`, `setName`,
`euroLabel`) for the server, the tests and analytics. The app never shows them; it uses
`ui/i18n/CoreText.kt`:

| Thing | In a composable | Anywhere (UiText) |
| --- | --- | --- |
| Depth zone | `zone.label()`, `zone.shortLabel()` | `zone.labelText()` |
| Rope stat | `stat.label()`, `stat.shortLabel()`, `stat.description()` | `...Text()` |
| Tackle | `tackle.label()`, `tackle.description()` | `...Text()` |
| Battle mode | `mode.label()`, `mode.rules()` | `mode.labelText()`, `mode.rulesText()` |
| Battle score line | | `score.labelText(mode)` |
| Bot difficulty, challenge difficulty | `label()` | `labelText()` |
| Skin, slot, set | `cosmetic.label()`, `cosmetic.description()`, `slot.label()`, `skinSetLabel(setName)` | `...Text()`, `skinSetText()` |
| Worn title (an English id, relayed online) | `titleLabel(title)` (`TitleBadge` does it) | `titleText(title)` |
| Shop item | `item.label()`, `item.description()` | `item.nameText()`, `item.descriptionText()`, `item.badgeText()` |
| Premade boat | `boat.label()`, `boat.description()` | `boat.nameText()`, `boatNameText(id)` |
| Boat colour, pattern, sail, flag, emblem, print | `swatchLabel(name)`, `pattern.label()`... | `swatchText(name)`, `...labelText()`; `partName(part)` |
| Atmosphere, title frame, card style, decor, theme, night mode | `label()` (+ `description()`) | `labelText()` |
| Season, event goal, tournament tier, club role, board, board view | `season.label()`, `label()` | `season.nameText()`, `labelText()` |
| Species | `species.label()` | `species.nameText()` |
| Secret title (only once found, never in the tour) | `secretTitle(id)` | `secretTitleText(id)` |
| Obstacles | `kind.label()` | `kind.labelText()` |

Euro prices: `formatEuros(cents)` (Google Play's own formatted price wins when the store has it).

Adding to core: a new enum entry won't compile until its `when` in `CoreText` has a string; a new
shop item, boat, season name or species fails `CoreTextTest` until it has one. Write the English
resource equal to core's English (`CoreTextTest` compares them).

`HardcodedTextTest.coreEnglishNamesAreNeverShown` fails on `.displayName`, `.setName`, `.tagline`,
`.summary`, `.euroLabel` or `mode.title` in app code. Lines that use one as an id (not shown) say
so with `// i18n-ignore: <why>`. Files still listed in `PENDING_CORE_NAMES` put a core name in an
English sentence: remove them from the list when you convert them.

## 7. Numbers, units, times (`ui/Format.kt`)

| Need | Use |
| --- | --- |
| Whole number with grouping | `formatPoints(n)` / `UiText.number(n)` |
| "1,250 pts" | `pointsLabel(n)` / `pointsText(n)` |
| "5k", "1.5M" (chips) | `compactPoints(n)` / `compactPointsText(n)` |
| "120 m", "10 kg", "4.5 m/s" | `formatStat(stat, value)` / `statText()`, `metersText(m)` |
| Decimal | `formatDecimal(x)` / `UiText.decimal(x)` |
| "21:00" | `hourText(h)` |
| "2 d 5 h", "45 min" | `ShopText.timeLeft(ms)`, `ShopText.fishingTime(min)` (`...Label()` in composables) |
| "€0.99" | `formatEuros(cents)` |

The default `Locale` follows the game's language, so these are right outside composables too.

## 8. What not to translate

- Analytics event, parameter and property names (`GameAnalytics`, `docs/analytics-events.md`).
- Preference keys, test tags, store product ids, server paths, protocol fields, enum names.
- Names players type, player ids, room codes, recovery codes ("DL-..."), club codes.
- Generated club names and strangers' public names on the boards (same for everyone).
- The brand "Deep Line" (`app_name` is `translatable="false"`), "Google Play", "Google Firebase",
  "CC0", "OpenGameArt.org", "UTC". Credits keep the works' titles and authors.
- The battle server's English error text, until mapped (see the online area below).

## 9. Tests and checking your work

```bash
# Quality harness (fast, plain JVM)
./gradlew :app:testDebugUnitTest --tests '*LocalizationResourcesTest' --tests '*HardcodedTextTest' --max-workers=2
# Names from core, UiText, the tour, the language picker (Robolectric)
./gradlew :app:testDebugUnitTest --tests '*CoreTextTest' --tests '*UiTextTest' --tests '*TourContentTest' --tests '*Language*' --max-workers=2
# Everything before you commit
./gradlew :core:test :server:test :app:testDebugUnitTest --max-workers=2
```

- **Converted files**: when a file has no hard-coded text left, add it to
  `HardcodedTextTest.CONVERTED` (path from `app/src/main/kotlin/com/deepline/fishing/`). From then
  on any literal with letters in a `Text(...)` or text parameter, or any literal that looks like
  words, fails the test. Keys, tags and ids are fine; a deliberate exception carries
  `// i18n-ignore: <why>` on its line.
- **UI tests** run in English under Robolectric and find nodes by text: keep the English text the
  same when you move it to a resource, or look it up with `str(R.string.x)` (better: it survives
  wording changes). Values built with `UiText` are compared with `.text`.
- **The tour**: `TourContentTest` reads the English tour and checks topics, numbers from
  `GameConfig` and that no secret is given away. Tour text that names a label (a tab, a tackle, a
  zone, a mode) must use the label's exact words.
- **Layouts**: in a debug build, Settings > Language offers English (XA) (accented, ~30% longer,
  bracketed: any plain English is hard-coded, and a sentence split across brackets is
  concatenated) and Bidi (ar-XB) (right to left). `LanguageUiTest` checks the Settings screen
  in English (XA). Once translated, check your screens in German and French (the longest): no
  clipped buttons or chips, wrapping instead of ellipsis for anything the player must read.

## 10. Translators

You translate one language, in its folder. For each English file `values/strings_<area>.xml`:

1. Create `values-<lang>/strings_<area>.xml` with the **same file name** and **every key** (a file
   that exists must be complete: the test fails on missing or extra keys). Keep the header
   comment (translated or not); string comments are optional.
2. Translate the values. Keep every placeholder (`%1$s`, `%2$d`) with its number and type, in the
   order your language needs. Keep `\n`, the quoting of values in double quotes, and `%%`.
3. Plurals: write exactly the forms of your language (section 4).
4. Don't copy `translatable="false"` strings (`app_name`).
5. Follow the glossary below, and record your choices for the game's terms in
   `docs/l10n-glossary/<lang>.md` (one file per language: zones, tackle, modes, bumps, Dex...) so
   every screen uses the same words. Species: translate each generator word once (section 11) and
   reuse it.
6. Respect length: short labels (tabs, chips, buttons) should stay close to the English length;
   comments say when something is tight.
7. Typography: French puts a no-break space before `:`, `!`, `?`, `;` and inside « » (write
   ` ` or ` `); Spanish opens questions and exclamations (`¿`, `¡`); German capitalises
   nouns; use your language's quotation marks.

Run `LocalizationResourcesTest` as you go. **When your language is complete**, add its folder
suffix to `LocalizationResourcesTest.REQUIRED_LANGUAGES` (`"fr"`, `"es"`, `"de"`, `"pt-rBR"`,
`"tr"`): from then on a missing file or key fails the build. That's the switch from "checked as you
go" to strict.

## 11. Glossary

How each game term is treated. "Translate" means one translation, used everywhere (record it in
your language's glossary file). Resource keys show where the term is the label.

| Term | Meaning | Treatment |
| --- | --- | --- |
| Deep Line | The game | Never translate |
| Fish (tab) | The hub tab where you cast | Translate (`shell_tab_fish`), short |
| Dex | The collection of species cards | **Keep "Dex"** (a coined name, like a proper noun); adapt only the spelling if your language must |
| Species, Seen, Caught | Dex entries and their states | Translate; "Seen" = met but not caught |
| Cast (noun and verb), Cast line | One fishing run; the main button | Translate (`home_cast_button`) |
| Hook, line, rope | The hook; the line; the rope is the upgradable line (Rope Length/Strength/Agility) | Translate; keep "rope" distinct from "line" (skins have a Line slot) |
| Bump, bump-free | Touching a fish's head or an object on the way down; the first meters where bumps don't count | Translate as one word used everywhere (tour, HUD, rules, secrets) |
| Head | The front of a fish: where you hook it | Translate |
| Load | The weight bar during a cast | Translate |
| Reel in fast, fast reel | The button that hauls a full line up | Translate |
| Tackle | One-cast items (Shop page) | Translate |
| Lantern | Tackle that lights dark water | Translate as a name (capitalised in English); `common_lanterns`, `core_tackle_lantern` |
| Rare fish detector, Lucky charm, Sparkle lure | The other tackle | Translate as names (`core_tackle_*`) |
| Sparkling | A rare golden fish state worth 3× | Translate (adjective), same word everywhere |
| Sunlit Shallows, Twilight Reef, Midnight Trench, Abyssal Floor | The four depth zones | Translate as place names (`core_zone_*`), with short forms (`_short`) |
| Points, pts | Score and currency | Translate; "pts" is the short unit (`common_points`) |
| Milestones | Rewards for lifetime points | Translate |
| Fishing secrets, Secrets | Hidden tricks, found by playing | Translate; secret titles (`core_secret_*`) are catchy move names |
| Skins, Title, title badge, Dex cards | Looks; text under the name; its frame; card styles | Translate |
| Atmosphere: Family, Nightmare | Moods of the deep | Translate |
| Themes; Day, Night, Like my phone | Menu looks and their night mode | Translate |
| Rope, Tackle, Style, Passes, Aquarium | Shop pages | Translate (`shell_shop_page_*`) |
| Captain's Club | The monthly membership | Translate as a name, consistently (also in the store listing) |
| Season pass, Collector's edition, Weekend event, Starter pack, Founder's pack | Passes | Translate as names |
| Best of 5, 3-Minute Rush, Heavy Haul, Species Hunt, Deep Dive | Battle modes | Translate as names (`core_mode_*`) |
| Easy, Normal, Hard, Rival | Computer opponent levels; the computer opponent | Translate |
| Quick match, room code | Online pairing; the 5-character code | Translate (the code itself never) |
| Wager, stake | Points bet against the computer | Translate |
| Gift, cloud save, recovery code | Account features ("DL-..." codes) | Translate; never the code |
| Player id, friend code | The public id used to add friends | Translate |
| Daily reward, streak, streak shield | Daily calendar, days in a row, protection for a missed day | Translate |
| Daily challenges, swap | Three daily goals; replacing one | Translate |
| Migration | Two species more common for 48 h each week | Translate |
| Friends and rankings, leaderboards | The social hub and its boards | Translate |
| Best cast, Deepest catch, Rarest catch; Everyone, League, Friends | Boards and their views | Translate (`core_board_*`, `core_view_*`) |
| Weekly tournament, attempt, Tournament title badge | Same water for everyone, 5 attempts | Translate |
| Club, owner, member, club goal | Clubs (shipping later) | Translate; club names stay as generated |
| m, kg, m/s, ×, UTC, 00:00 | Units, multiplication sign, the reset time | Keep the symbols; add your language's spacing |

**Species words.** Generated species are an adjective plus a kind of fish, from these fixed lists
(core `SpeciesGenerator`). Translate each word once and build names from your translations, in
your language's order and agreement; keep all 200 names unique.

- Shallows: Sunny, Golden, Speckled, Banded, Lemon, Rosy, Pebble, Minted, Sandy, Copper, Bright,
  Lagoon / Goby, Minnow, Wrasse, Chub, Shiner, Mullet, Blenny, Damsel, Tetra, Gudgeon
- Twilight Reef: Dusk, Indigo, Violet, Amber, Velvet, Crimson, Twilight, Saffron, Cobalt, Mauve,
  Ember, Tidal / Snapper, Grouper, Lionfish, Triggerfish, Parrotfish, Tang, Barracuda, Batfish,
  Hogfish, Angelfish, Cardinal, Ray
- Midnight Trench: Ghost, Ink, Iron, Obsidian, Phantom, Shadow, Hollow, Ashen, Smoke, Gloom,
  Silent, Pewter / Hake, Viper, Hatchetfish, Dragonfish, Bristlemouth, Barreleye, Fangtooth,
  Sabretooth, Snipe Eel, Loosejaw, Sturgeon, Cuttle
- Abyssal Floor: Abyssal, Void, Ancient, Crystal, Starlit, Hadal, Eternal, Pallid, Rune, Frost,
  Nebula, Spectral / Angler, Gulper, Grenadier, Snailfish, Seadevil, Ghostshark, Tripodfish, Cusk,
  Tonguefish, Wyrm, Drifter, Chimaera
- Hand-made: Silver Sprat, Coral Darter, Reed Perch, Sun Bream, Dusk Snapper, Ribbon Eel, Reef
  Grouper, Veil Ray, Ghost Hake, Needle Viper, Ink Cuttle, Iron Sturgeon, Glowfin Angler, Star
  Drifter, Abyss Wyrm, Pale Leviathan, and the legendary Deep Sovereign (a secret until found).

Use real fish names where they exist in your language (goby, wrasse, grouper...).

## 12. Adding a language

1. `AppLanguage` (`ui/i18n/AppLanguage.kt`): an entry with its BCP 47 tag and its own name.
2. `res/xml/locales_config.xml`: the same tag (the test compares them).
3. `app/build.gradle.kts` `androidResources.localeFilters`: the folder form ("pt-rBR").
4. `LocalizationResourcesTest.PLURAL_QUANTITIES`: its CLDR plural forms.
5. Translate into `values-<folder>/` (section 10), then add it to `REQUIRED_LANGUAGES`.
6. Update the tour's language step (`tour_language_body`), README's Languages section and the
   store listing.

## 13. Converting an area (the next agents)

Work file by file: move every string to your area's file with a comment, use `CoreText` for core
names and `Format.kt` for numbers, make messages `UiText`, run the tests, then add the file to
`HardcodedTextTest.CONVERTED` (and remove it from `PENDING_CORE_NAMES` if it is there). Keep
English wording unless it is wrong (the UI tests look for it). Commit per file or small group.

Already converted (step 1): the shell (`MainActivity`, `AppRoot`, `MainScaffold`), `Format.kt`,
`HomeScreen`, `MilestonesScreen`, Settings and all its sections, the consent sheet and privacy,
the tour, `GameViewModel`'s messages, `PrivacyController`, `SocialController` and
`DailyController` messages, `ShopText`, `BoatText`, `DailyText`, `AccountClient`,
`OnlineConnection`, and every use of a core name.

What is left, in three areas of about the same size (rough count of strings):

**Area A: casting, battles, online and secrets (~225)**

- `ui/CastScreen.kt` (~45): HUD, pause, results. `ui/CastRenderer.kt` (~6): canvas labels
  ("Bump-free above %1$d m", "Rope limit", depth marks): resolve in `CastScreen` and pass them
  down (the 2D overlay draws them; keep `drawLabel`).
- `ui/BattleScreen.kt` (~43), `ui/BattleSetupScreen.kt` (~25), `ui/OnlineLobbyScreen.kt` (~20).
- `ui/secrets/FishingSecrets.kt` (~78: bodies, bonuses, reward text, the discovery dialog; in
  `PENDING_CORE_NAMES`) and `ui/dex/SecretDex.kt` (~7). Titles are done (`core_secret_*`).
  Never let a secret's text reach the tour.
- Online: the server's English errors (`ServerMessage.Error`, shown through `UiText.raw` in
  `GameViewModel.onServerMessage`) and core `OnlineMatch.overReason`: map the known English texts
  to resources (keep the raw text as a fallback); don't change the server's protocol.

**Area B: Dex, skins, boat editor, shop, themes and aquarium (~240)**

- `ui/shop/ShopScreen.kt` (~80), `ui/shop/GiftsUi.kt` (~21), `ui/shop/SeasonUi.kt` (~16),
  `ui/shop/ThemesScreen.kt` (~11), `ui/shop/AquariumScreen.kt` (~8). Replace ShopScreen's private
  `euros()` with `formatEuros`.
- `ui/dex/SkinDex.kt` (~29), `ui/dex/FishDetailScreen.kt` (~25), `ui/dex/SpeciesDex.kt` (~8),
  `ui/dex/DexScreen.kt` (~3), `ui/dex/DexStyle.kt` (~2: "Legendary", "Tier %1$d").
- `ui/boat/BoatEditorScreen.kt` (~26).
- `billing/TestStore.kt` (~7), `billing/PlayStore.kt` (~3): the messages passed to
  `StoreListener.onProblem` (make them `UiText`, or problem codes the view model words).

**Area C: daily loop, reminders and social (~230)**

- `ui/daily/DailyUi.kt` (~37).
- `daily/Reminders.kt` (~21: notification titles, texts and channel names; in
  `PENDING_CORE_NAMES`): built in a WorkManager worker, so resolve with
  `AppLanguages.localized(context)`; species and zones with `nameText()`/`labelText()`.
- `ui/social/ClubsScreen.kt` (~39), `ui/social/SocialHubScreen.kt` (~33),
  `ui/social/TournamentScreen.kt` (~29), `ui/social/FriendsScreen.kt` (~28),
  `ui/social/LeaderboardsScreen.kt` (~22), `ui/social/SocialUi.kt` (~19).

Not player text (leave as is): render code (`ui/render3d/*`, shader and uniform names),
`GameIcons`, `FishArt`, `ColorScript`, analytics, audio file names, `AudioCredits` (titles and
authors of the works, licence names).
