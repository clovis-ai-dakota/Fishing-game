# Menu design: rules and kit

How the menus are designed since the interface redesign: less text, actions that stand out by
colour, one coherent world. The components are in `app/src/main/kotlin/com/deepline/fishing/ui/kit/`.
The analysis behind these rules (what the most played games do, with sources) is summarised at
the end.

## 1. Words: names, numbers and pictograms

- A main screen shows **names, numbers and pictograms**, not sentences. The first view of a tab
  has a **word budget** (`WORD_BUDGET` in `LocaleLayoutTest.kt`: 40 words of two letters or more, in
  every language; numbers and one-letter units don't count). The test writes each screen's count to
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
| `POINTS` | an ivory price tag with a gold rim and the coin | A price in points. Never the amber of `PLAY`: a price must not look like the main action or a free reward. The number turns red when the player can't afford it. |
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
| `RoundIconButton` | A pictogram button over a scene or in a top bar, with a red count (`badge`) and a caption (`label`: a countdown, a count). `CLAIM` role when something is ready. The circle and its caption are one tap target. |
| `TackleTile` | One kind of tackle: pictogram, how many left, lit when in use; empty, it leads to the Shop. |
| `ScenePill`, `StatPill`, `CoinAmount` | A number with its pictogram, readable on any scene. |
| `SectionTitle`, `InfoButton` | A title with its (i). |
| `Pips`, `RingProgress` | Levels and progress without words. |
| `Modifier.dockBackground()` | The deep water under the action zone of a tab. |
| `sceneChipColors()` | For chips placed straight on the scene (the Shop's pages, the Dex's filters): glass like a card, never see-through. |

A tab's layout: the content (a scene or a list), then a **dock** on deep water holding the main
action, then the tab bar. The main action never scrolls away.

The phone's own navigation bar always stands on deep water too: the tab bar reaches under it, and
a pushed screen (`SubScreen`) paints that strip, so the scene's seabed never shows under the
system buttons.

## 4. One world, and moves that mean something

The menus are places along one coast (`Place` in `ui/theme/World.kt`), in the tabs' order:

| Place | Menus | What shows it |
| --- | --- | --- |
| `SEA` | Fish tab and what opens from it (Daily, Milestones, Themes, Settings) | The boat's scene. |
| `DEEP` | Dex, a species' page, the aquarium | The camera sinks: the waterline leaves by the top, the water darkens. |
| `PORT` | Shop | A quay, a market stall, moored masts, warm morning light. |
| `ARENA` | Battle setup, battles, the online lobby | The setting sun and two rival boats. |
| `LIGHTHOUSE` | Friends, leaderboards, the tournament, clubs | A lighthouse and its beam, evening blue. |

- `ThemeBackdrop(place = ...)` moves a camera between places (`CAMERA_MS`, 280 ms) and the scene's
  layers slide by at different speeds (sky 0.25, shoal 0.5, particles 0.75, decor 1): parallax.
- What stands on the coast is **far away**: small, washed towards the sky's colour (`far()`), and
  under the screens' titles. It must never look like something to tap, nor make text hard to read.
- `ScreenMotion` decides every move, and none lasts a second: tabs slide the way the camera goes
  (sideways along the coast, down for the Dex); a pushed screen grows out of the menu (240 ms) and
  shrinks back faster (170 ms); casting **dives** (400 ms) and collecting **surfaces** (300 ms).
  The numbers follow NN/g's guidance: about 100 ms for feedback, 200 to 300 ms for a screen
  change, 400 ms only for a big move, never 500 ms, and leaving faster than arriving.
- A new menu gets a place (add it to `Place.of`); a new place gets a light and something on the
  coast, in every theme, by day and by night.

## 5. Depth

Menus feel like objects, without a 3D view per item (one live GL view per screen at most: the
boat on the Fish tab, the fish on a species' page, the skins preview):

| Effect | Where | Code |
| --- | --- | --- |
| Raised buttons that sink | Every `GameButton`, `RoundIconButton`, `TackleTile` | `drawRaised` |
| Cards that lean under the finger, with a sheen | Dex cards, secrets, skins, shop tiles, battle tiles, social hub tiles | `Modifier.pressTilt()` |
| The scene shifts as the phone tilts | The backdrop's layers (near ones more) | `rememberDeviceTilt`, `depth()` |
| A chest that opens | The daily reward | `RewardChest` (a box model projected by hand) |
| Coins fly to and from the balance | A `CLAIM` button's coins fly to the points pill (`Modifier.rewardBalance()`), a collected cast's points fly to it, and points spent fly out of it to what was bought; a fountain where no balance is on screen | `RewardFx`, `RewardFxLayer` |

All of it is looks only, costs nothing when idle (the tilt sensor stops in the background, the
burst layer only draws while coins fly), and **stops with "Reduce motion"** (`LocalCalmMotion`):
no tilt, no burst, the chest just opens, screens fade.

## 6. Checked on a real phone

The menus were run on a mid-range phone (Fairphone 4, Adreno 619, 1080 x 2340, 60 Hz) and measured
with `adb shell dumpsys gfxinfo <package> framestats` (per-stage times of the last 120 frames) and
`simpleperf` on the render thread. What it taught, and the rules that follow:

- **The phone's GPU is the limit, not Kotlin.** The scene moves, so the whole window is redrawn on
  every frame: a menu at rest costs about 12 ms of GPU a frame (70 to 80% busy at 60 fps), of which
  the light rays are about 2 ms, the places' light 1.3 ms and the seabed decor 0.6 ms; the rest is
  the sky, the sea and the glass cards. Keep what a frame draws cheap before adding anything.
- **Don't draw the scene where nothing of it shows.** The Fish tab's boat scene, dock and tab bar
  cover the whole window, so the backdrop isn't drawn under it (`BackdropCover`): only once the
  tab is at rest (`LocalScreenSettled`) and its scene has painted a picture. That took the Fish
  tab from 75% to about 34% GPU at rest. A new screen that covers the window can do the same.
- **Never draw a big path that changes on every frame.** A path that isn't convex is rasterised on
  the CPU at its full size and uploaded again each time it changes: the sea, as one path with a
  wavy top, cost about 10 ms a frame in every menu. Anything "below a wavy line" goes through
  `fillUnderWave` (a thin band for the wave, a rectangle for the rest). Small paths, convex shapes,
  circles, rectangles and lines are fine.
- **Tall screens show more sky.** The cast's 3D view starts with over 8 m of sky on a 19.5:9
  phone and the Fish tab with more: nothing may depend on how much sky is in view
  (`SceneBuilder.boatInView`, tested in `Render3dTest`).
- **A question never covers a reward.** The reminder explainer waits for the chest to open and
  the coins to land (`REVEAL_MS` in `DailyUi.kt`).
- Tests and Robolectric have no navigation bar, no GPU and one screen size: after changing the
  scene, a transition or a screen's frame, look at it on a phone.

Transitions, measured in a release build (R8, compiled ahead of time like a Play install; debug
builds overstate composition three to four times): the tap's frame composes and lays out the next
screen (50 to 80 ms here for the Dex, the Shop, Milestones and Daily, under 20 ms for the social
screens), then the move starts. Logging the transitions' clock on every frame showed that a
tab's slide then runs on every frame without a jump; a screen pushed from the Fish tab can still
lose two or three frames while both screens fade (two full-screen alpha layers on top of the
scene). Drawing the scene at 30 fps would halve the GPU load of menus at rest (74% to 38%) but
didn't help the transitions, and leaving out the rays or cutting the tabs' fade didn't either:
what is left is the cost of composing the next screen. To go further, make the heavy screens
cheaper to compose, not the moves shorter.

## 7. Patterns taken from the most played games

Checked against real screens (Clash Royale, Brawl Stars and Clash of Clans on interfaceingame.com)
and the articles below, read in full:

- **Every icon button has a word under it** (`RoundIconButton(caption = ...)`): Supercell labels
  its home buttons (Shop, Brawlers, Friends...). A pictogram alone is a guess; a pictogram and one
  word is learnt once. Numbers that matter now (a countdown, a count) go on the same chip.
- **Yellow for the main action, green for free things, red for notifications, blue everywhere
  else**, and never the main action's colour on a price: Clash Royale was criticised when a paid
  item's button turned yellow, and again when it stopped showing unaffordable prices in red.
  Hence the ivory `POINTS` tag and the red number.
- **One cast feeds everything, and the game shows it**: Royal Match advances every event with its
  single Play button and shows the progress after each level. `CastProgressStrip` does it on a
  cast's results: the day's challenges, the season, new species, milestones ready.
- **Resources travel**: coins fly from where they were won to where they are kept, and out of it
  when spent (Royal Match does it for every resource), so no text is needed to say where points
  come from or go.
- **A reward is revealed as things, not as a sentence**: a claim's contents pop in as big tiles
  (`GotTile` on the Daily screen), like a chest's "You got".
- **A reward track is a road**: the milestones run along one progress line with a node each.
- **One hand**: the main action and the tabs are in the lower half; back is the system gesture.
- **Never more than one popup when the game opens**, and the same (i) everywhere for details.
- **Undo**: a rope upgrade can be taken back for a full refund (the Brawl Stars critique asks
  for exactly this).

Not done, and why: swiping between tabs (Clash Royale allows it; ours would fight the horizontal
lists of the Shop and the Dex), and half-height sheets instead of full screens for Daily or
Milestones (Clash Royale keeps the previous screen visible behind its popups).

Sources read in full: Pratt IxD, "Design Critique: Brawl Stars"; The Rookies, "Game Design UX
Best Practices: Detailed Breakdown of Clash Royale"; Funovus, "Royal Match Dominates Match-3";
UserWise, "5 Simple UX Lessons From Royal Match"; Nielsen Norman Group, "Executing UX Animations:
Duration and Motion Characteristics". Known only through search summaries (their sites refuse
automated readers): UX Planet's game UX guide and two Medium articles. The Game Juice article
cited earlier is no longer online, so nothing here relies on it.
