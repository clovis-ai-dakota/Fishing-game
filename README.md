# Deep Line — a fishing collection game for Android

An original fishing mini-game for Android, written in Kotlin with Jetpack Compose.
All art is drawn in code (no image assets).

## How it plays

- **Descent.** The hook sinks from the boat. Drag left/right to steer it. Each object the hook
  touches (a fish, rock, driftwood or jelly cluster) uses up one of **4 bumps**. After the 4th
  bump, or at the end of the rope, the hook reverses direction. Bumping fish on the way down
  therefore pulls the line up early.
- **Ascent.** On the way up, nudge a fish's **head** with the hook to catch it. Touching its
  body or tail does nothing. Fish keep being hooked until the rope's **max weight** is reached.
- **Scoring.** Points = species base points × depth multiplier (1× at the surface, +1× per
  100 m). **Sparkling** fish (gold outline and glints) are worth 3× more.
- **Lantern.** Below 300 m the water goes dark and you only see a small circle around the hook.
  A Lantern (toggle it on before casting; one is used per cast) widens the lit circle.

### Around the casts

There is no time limit: it is an ongoing collection game. Explore deeper zones as your rope
improves and try to fill the fish log.

- **Fish log.** 16 species across 4 depth zones (Sunlit Shallows, Twilight Reef, Midnight
  Trench, Abyssal Floor). It records count, Sparkling count, deepest catch and best score per
  species. Undiscovered fish show as silhouettes.
- **Points and milestones.** Every catch earns points. Milestones are based on the lifetime
  total earned, so spending points never loses milestone progress. Milestones reward bonus
  points and Lanterns.
- **Rope upgrades** (paid with points): Length (max depth, which unlocks deeper zones),
  Strength (max weight) and Agility (steering speed), 10 levels each.

### 1v1 battles

Against the computer (Easy / Normal / Hard) or a friend on the same device (pass and play).
Both players use the device's current rope and fish **the same water** for the same cast
number (or round), so skill decides. No lanterns in battles, and battle catches don't count
towards points or the fish log. The record against the computer is saved.

| Mode | Rules |
| --- | --- |
| Best of 5 | One cast each per round, higher score takes the round, first to 3 rounds wins. Tied rounds count for nobody (capped at 9 rounds, then the score decides). |
| 3-Minute Rush | 3 minutes of fishing each; only the single best cast counts. A cast still running when time is up ends on the spot and scores the fish already on the line. Pausing stops the clock. |
| Heavy Haul | 3 casts each; most total kilograms wins. |
| Species Hunt | 3 casts each; most different species wins, total points break ties. |
| Deep Dive | 3 casts each; the single deepest catch wins, total points break ties. |

The computer rival plays by exactly the same rules (see `core/.../Bot.kt`): it dodges objects
on the way down and aims for fish heads on the way up. Its difficulty changes how far ahead it
looks, how accurately it aims and how quickly it reacts. Quitting a battle cast forfeits.

## Assumptions to review

These details weren't in the spec, so I picked values. All numbers live in
`core/src/main/kotlin/com/deepline/fishing/core/GameConfig.kt` (plus the tables in
`FishSpecies.kt` and `Upgrades.kt`) and can be changed in one place.

| Topic | Choice |
| --- | --- |
| What counts as a "touch" on the way down | Any fish *or* obstacle, each counted once per cast. A bumped fish darts away but can still be caught on the way up. |
| Ascent hit on a fish that is too heavy | Nothing is caught; a "Too heavy" notice is shown. |
| Head zone | The front third of the fish, in the direction it faces. |
| Depth scoring | `base × (1 + depth / 100 m)`; Sparkling = ×3, spawn chance 6 %. |
| Lantern | Start with 1; earned from milestones, or 600 pts in the upgrades screen. |
| Battle opponents | Offline only (computer or pass and play); online play would need a server. |
| Battle modes beyond the two requested | Heavy Haul, Species Hunt, Deep Dive. |
| Bait / cast limit | None (bait was dropped from the spec): casting is unlimited. |
| Quitting a cast | Pause → *Quit cast* returns to the menu with no points; a lantern used for it is spent. |
| Fish, obstacles, upgrade costs, milestones | Original tables in `FishSpecies.kt` / `Upgrades.kt`. |

## Project layout

```
core/   Pure Kotlin game logic (no Android dependency) + unit tests
        CastSession.kt     descent / ascent rules, collisions, events
        Entities.kt        fish, obstacles, hit-tests (head vs. body)
        Scoring.kt         points and lighting
        WorldGenerator.kt  spawns fish/obstacles by depth zone
        Progress.kt        points, milestones, upgrades, fish log
        Battle.kt          1v1 battle modes, turns, scoring
        Bot.kt             computer rival
        Persistence.kt     save/load (key=value text)
app/    Android app (Jetpack Compose)
        GameViewModel.kt   game state, persistence via SharedPreferences
        ui/CastScreen.kt   game loop, HUD, pause, results
        ui/CastRenderer.kt underwater scene drawn on a Canvas
        ui/*Screen.kt      home, upgrades, fish log, milestones, battle setup / hub
```

## Building

Requirements: JDK 17+, Android SDK (API 36).

```bash
./gradlew :core:test            # game-logic unit tests (no Android SDK needed)
./gradlew :app:assembleDebug    # APK at app/build/outputs/apk/debug/
./gradlew :app:installDebug     # install on a connected device / emulator
```

The `:app` module is only included when an Android SDK is found (`ANDROID_HOME`,
`ANDROID_SDK_ROOT`, or `sdk.dir` in `local.properties`), so `:core` can be built anywhere.

CI (`.github/workflows/android.yml`) runs the tests and builds a debug APK on every push. The
APK can be downloaded from the workflow run's artifacts (`deep-line-debug-apk`).
