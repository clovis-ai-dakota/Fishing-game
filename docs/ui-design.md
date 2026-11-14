# Menu design: rules and kit

How the menus are designed since the interface redesign: less text, actions that stand out by
colour, one coherent world. The components are in `app/src/main/kotlin/com/deepline/fishing/ui/kit/`.
The analysis behind these rules (what the most played games do, with sources) is summarised at
the end.

## 1. Words: names, numbers and pictograms

- A main screen shows **names, numbers and pictograms**, not sentences. The first view of a tab
  has a **word budget** (`WORD_BUDGET` in `LocaleLayoutTest.kt`: 40 words with a letter, in every
  language; numbers don't count). The test writes each screen's count to
  `app/build/screenshots/locales/<lang>/words.txt` and fails over budget.
- **Explanations go behind an (i)** (`InfoButton`, or `SectionTitle(title, info = ...)`) and in
  the tour: the text still exists, on demand. Never a permanent paragraph under a title.
- A pictogram replaces a label only where it is unambiguous or repeated everywhere (the coin for
  points, the hook for a cast). Every pictogram-only control has a `contentDescription` (the
  sentence it replaced), which screen readers and tests use.
- **Pictograms are drawn in code** (`GameIcons`, `KitIcons`, `DailyIcons`): one style, tinted by
  the theme. Never system emoji: their drawing changes with the phone's maker, and they ignore
  the theme and night mode.
- Counts and amounts: `×4` (`common_times`), `50 / 200` (`common_fraction`), the coin and a number
  (`CoinAmount`, `CoinPrice`) instead of "4 owned", "50 of 200", "85,000 pts".

## 2. Colour says what an action is

`ActionRole` (in `GameColors.kt`) decides a button's colour, the same in every menu theme, by
day and by night:

| Role | Colour | For |
| --- | --- | --- |
| `PLAY` | amber | The **one** main action of a screen: Cast line, Start the battle, Collect. At most one per screen. |
| `CLAIM` | green | Something free and ready: daily reward, milestone, season tier, gift. |
| `NAV` | the theme's colour | Open, choose, go back, and every secondary action. |
| `POINTS` | gold, with the coin | A price in points. |
| `PREMIUM` | violet | A price in euros, passes, the Captain's Club. |
| `ALERT` | red | No way back (delete). Notification badges use the same red (`GameColors.Badge`). |

- The sea, the sky and the cards share one family of blues, so an action in the theme's own colour
  melts into the scene: that is why actions are warm and the theme colour is kept for navigation.
- Amber on a pale card only has about 1.7:1 of contrast: a raised button therefore always has a
  **darker edge and outline** (`drawRaised`), and the action zones sit on **deep water**
  (`deepColor()`: the dock under a tab's content, the tab bar, pills over a scene), where it
  reaches about 4:1.
- Text keeps 4.5:1 on its background (the existing theme rule). Don't hard-code colours outside
  `GameColors` and the theme.

## 3. The kit

| Component | Use |
| --- | --- |
| `GameButton(role, size)` | Every button that does something. Raised, sinks when pressed (90 ms), plays the tap through `LocalPressFeedback`. Sizes: `HERO` (the main action, in the thumb zone), `MEDIUM`, `SMALL`. |
| `RoundIconButton` | A pictogram button over a scene or in a top bar, with a red count (`badge`) and a caption (`label`: a countdown, a count). `CLAIM` role when something is ready. |
| `TackleTile` | One kind of tackle: pictogram, how many left, lit when in use; empty, it leads to the Shop. |
| `ScenePill`, `StatPill`, `CoinAmount` | A number with its pictogram, readable on any scene. |
| `SectionTitle`, `InfoButton` | A title with its (i). |
| `Pips`, `RingProgress` | Levels and progress without words. |
| `Modifier.dockBackground()` | The deep water under the action zone of a tab. |

A tab's layout: the content (a scene or a list), then a **dock** on deep water holding the main
action, then the tab bar. The main action never scrolls away.

## 4. What the most played games do (summary of the analysis)

- One home scene, one hero, one big button (Brawl Stars' large yellow Play button; Royal Match's
  single Play button also drives its events).
- Colour encodes function (Clash Royale keeps yellow for its most important actions, with an
  animated shine); the accent is kept for the key action of a screen.
- Animation replaces text (Royal Match shows where resources come from and go).
- Short transitions that start from what was touched: 150 to 300 ms; a forced wait above about
  250 ms feels like lag.
- Drawn pictograms rather than text, and never emoji.

Sources: Pratt IxD critique of Brawl Stars; The Rookies' breakdown of Clash Royale; Funovus on
Royal Match; Game Juice on UI feedback; UX Planet's game UX guide; Transifex on localisation and
UI; EmojiFYI on emoji rendering. Read through search summaries: the pages were not reachable from
the development session.
