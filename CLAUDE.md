# Deep Line: notes for working on this repo

Android fishing collection game (Kotlin + Jetpack Compose) with a Ktor battle server.
Modules: `core` (pure-Kotlin rules, battles, bot, online protocol), `server` (Ktor WebSockets),
`app` (Compose UI). See README.md for the rules and layout.

## Rules

- **Keep the in-app tour up to date.** Every new player-facing feature (screen, mode, rule,
  upgrade, item) needs a step, or an updated step, in
  `app/src/main/kotlin/com/deepline/fishing/ui/tour/Tour.kt`. When you add steps, bump
  `Tour.VERSION` and set `sinceVersion = Tour.VERSION` on them: returning players then get a short
  "What's new" tour of just those steps. Point steps at UI with `Modifier.tourTarget(...)`.
  `TourContentTest` fails when a screen, battle mode or rope upgrade isn't covered; extend it
  when you add new kinds of features.
- All tunable numbers live in `core/.../GameConfig.kt` (plus the tables in `FishSpecies.kt` and
  `Upgrades.kt`). The tour and HUD read them, so don't hard-code them in UI text.
- Run `./gradlew :core:test :server:test :app:testDebugUnitTest` before pushing. App UI tests
  need JDK 21 (Robolectric emulating Android 16).

## Compose pitfalls already hit in this codebase

- Never call `drawText(textMeasurer, text, topLeft)` near the canvas edge: it throws when the
  text starts below the bottom edge. Use `drawLabel` in `CastRenderer.kt` (measure, then draw).
  `RenderFuzzTest` draws every frame of whole casts to catch render crashes.
- `CastSession` and `Battle` are mutable, non-observable objects. Don't pass them to child
  composables and expect them to refresh: Compose skips the child when the same instance is passed.
  Copy what's shown into plain data first (see `HudState`, `BattleView`). Unused parameters
  are also ignored for skipping.
- The UI tests only draw the game canvas when taking a screenshot, so rendering bugs need
  `RenderFuzzTest`, not the screenshot tests.
