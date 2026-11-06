# Analytics event taxonomy

Every event, screen view and user property Deep Line logs, with its parameters and why it exists.
All of them go through the facade `app/.../analytics/Analytics.kt`; the game's own events are
named in `analytics/GameAnalytics.kt`. Nothing reaches Firebase unless the player allowed
**usage statistics** (events, screen views, user properties) or **crash reports** (errors) on
the consent sheet or in Settings > Privacy (see `docs/research/analytics-privacy.md`).

**Keep this file up to date.** `AnalyticsTaxonomyTest` fails when an event name used in the code
(through `GameAnalytics` or a literal `Analytics.event("...")`) or a user property isn't listed
here in backticks, or breaks Firebase's limits (`AnalyticsRules`): snake_case names of at most 40
characters starting with a letter, no `firebase_`/`google_`/`ga_` prefix, no reserved names, at
most 25 parameters, text values of at most 100 characters; user properties at most 24 characters
long with values of at most 36, 25 at most.

**Never log personal data**: no player names, recovery codes, player ids (ours or an
opponent's), free text, email addresses or exact timestamps of the player's life. Game facts only.
True/false parameters are sent to Firebase as 1/0.

## Conventions

- `level_name`: what kind of cast. `solo`, `battle_<mode>` (against the computer or a friend on
  the same phone) or `online_<mode>`; modes are `best_of_five`, `time_attack`, `heavy_haul`,
  `species_hunt`, `deep_dive`.
- `zone`: a depth zone, `shallows`, `twilight`, `midnight` or `abyss`.
- `source` (points events): where points came from or went: `cast`, `milestone`, `secret`,
  `secret_bonus`, `season`, `weekend_event`, `wager`, `rope`, `rope_refund`, `tackle`, `shop`,
  `boat_part`, `gift`, `gift_refund`.
- Recommended Firebase events are used where one fits (`tutorial_begin`, `tutorial_complete`,
  `level_start`, `level_end`, `earn_virtual_currency`, `spend_virtual_currency`,
  `unlock_achievement`), so Google Analytics' game reports work without custom setup.

## Screen views

| Event | Parameters | Why |
| --- | --- | --- |
| `screen_view` | `screen_name`: `fish`, `dex_fish`, `dex_secrets`, `dex_skins`, `shop_rope`, `shop_tackle`, `shop_style`, `shop_passes`, `shop_aquarium`, `battle_setup`, `cast`, `fish_detail`, `milestones`, `battle`, `online_lobby`, `boat_editor`, `aquarium`, `themes`, `settings`, and any later screen's name in lower case. `screen_class`: always `MainActivity`. | Where players spend their time and how they move around. Logged by `TrackScreens` (automatic screen reporting is off: the app is a single activity). Screens the tour flips through aren't counted. With crash reports allowed, the current screen is also attached to crash reports as the `screen` custom key. |

## The guided tour

| Event | Parameters | Why |
| --- | --- | --- |
| `tutorial_begin` | `tour` (`full` or `whats_new`), `steps` | Recommended event: how many players start the tour (it starts right after the consent sheet on a first launch). |
| `tutorial_complete` | `tour`, `steps` | Recommended event: how many finish it. |
| `tutorial_skip` | `tour`, `step` (1-based), `step_id`, `steps` | Where players give up on the tour, to shorten or fix the steps they leave on. |

## Casts

| Event | Parameters | Why |
| --- | --- | --- |
| `level_start` | `level_name`, `rope_length_m`, `lantern`, `detector`, `lucky_charm`, `sparkle_lure` | Recommended event. Every cast (solo or a battle turn) is a "level": how often players fish, and how much tackle they use. |
| `level_end` | `level_name`, `success` (something landed), `end` (`surfaced`, `time_up` or `quit`), `depth_m`, `zone`, `catches`, `sparkling`, `snags`, `points`, `secrets` (secret bonuses in the cast), `bumps`, `turned_by` (`bumps`, `rope_end`, `none`), `fast_reel`, `lantern`, `detector`, `duration_s`. A quit cast has `phase` (`descending`/`ascending`), `depth_m`, `zone`, `catches`, `duration_s`. | Recommended event. The core loop's balance: how deep casts go, how much they earn, how often players quit. Checks the difficulty tuning (`DifficultyTest`) against real play. |
| `first_cast` | `depth_m`, `catches` | A new player's first solo cast collected: the first step of the onboarding funnel. |
| `first_catch` | `species_id`, `depth_m`, `casts_before` | A new player's first fish: how many casts it takes. |

## Points (the virtual currency)

| Event | Parameters | Why |
| --- | --- | --- |
| `earn_virtual_currency` | `virtual_currency_name` (`points`), `value`, `source` | Recommended event: where points come from. Compared with `EconomyTest`'s career model. |
| `spend_virtual_currency` | `virtual_currency_name` (`points`), `value`, `item_name` (a shop item id, `rope_<stat>_<level>`, a tackle id, a boat option, `wager_<rival>`), `source` | Recommended event: what players spend points on, and whether prices are right. |
| `rope_upgrade` | `stat` (`length`, `strength`, `agility`), `level` (the new level), `cost` | The main progression: how fast players climb each rope stat. |
| `rope_downgrade` | `stat`, `level`, `refund` | How often the full-refund downgrade is used. |

## Shop (euro purchases)

| Event | Parameters | Why |
| --- | --- | --- |
| `store_checkout` | `item_id`, `section` (`tackle`, `style`, `passes`, `aquarium`), `gift` | A euro payment was started (Google Play's sheet opened). |
| `store_purchase` | `item_id`, `section`, `gift` | The purchase was granted. **No price or currency**: Google Play purchases are measured by Firebase itself (`in_app_purchase`, automatic once the Firebase project is linked to Google Play), and logging revenue here too would count it twice. |
| `store_problem` | `item_id` (`unknown` when the store doesn't say), `problem` (`cancelled`, `pending`, `failed`, `unavailable`) | Where the payment funnel breaks. `failed` and `unavailable` are also reported as non-fatal errors (area `billing`). |

Points purchases are `spend_virtual_currency` with `source` `shop`.

## Rewards and secrets

| Event | Parameters | Why |
| --- | --- | --- |
| `unlock_achievement` | `achievement_id` (`milestone_1` to `milestone_15`) | Recommended event: a milestone claimed (its points are an `earn_virtual_currency`). |
| `season_claim` | `season`, `rewards` (tiers claimed at once), `premium` (any from the pass's track) | How the season track is used, free and premium. |
| `weekend_event_claim` | none | How many players finish a weekend event. |
| `secret_discover` | `secret_id`, `tier`, `in_battle` | Internal analytics: which fishing secrets players find, and how late. Never shown to players. |

## Battles

| Event | Parameters | Why |
| --- | --- | --- |
| `battle_start` | `battle_mode`, `opponent` (`easy`, `normal`, `hard`, `friend`, `online`), `stake` (points wagered, 0 if none) | Which modes and rivals are played. A wager is also a `spend_virtual_currency`. |
| `battle_end` | `battle_mode`, `opponent`, `result` (`win`, `loss`, `draw`; `player_1`/`player_2` for pass and play), `by_forfeit`, `stake`, `payout` | Win rates per rival (bot tuning), forfeits (connection problems, frustration). A payout is also an `earn_virtual_currency`. |

Nothing about the online opponent (name, id, title) is logged.

## Errors (crash reports)

Crashes are reported by Crashlytics automatically (when crash reports are allowed). Handled
problems are sent as non-fatal reports through `Analytics.recordError`, via
`GameAnalytics.problem(area, ...)`, once per kind per app session (20 at most):

| Area | When |
| --- | --- |
| `render3d` | The 3D renderer failed (shaders, a draw, a GL error): the app fell back to 2D (`Render3d.failed`). |
| `render3d_offscreen` | Dex portraits couldn't be drawn in 3D. |
| `render3d_preview` | The skins preview stopped. |
| `billing` | The store failed or was unavailable (with the store's message). |
| `account` | A gift, cloud save or restore request failed (the message shown to the player). |
| `account_delete` | Deleting the online account failed. |
| `gift` | A gift couldn't be delivered (it was refunded). |
| `online` | The battle connection closed, or was lost during a match (only the kind of failure: the reason can contain the server's address). |

## User properties

Set from the progress (`GameAnalytics.progressChanged`), sent only when they change, and only
while usage statistics are allowed (the latest values are sent right after the player agrees).

| Property | Values | Why |
| --- | --- | --- |
| `rope_band` | `0`, `1-24`, `25-49`, `50-74`, `75-99`, `100`: the share of all rope levels bought | Segment every report by progress. |
| `reach_zone` | the deepest zone the rope reaches | The same, in game terms. |
| `dex_band` | the same bands, for the share of species caught | Collectors versus casual players. |
| `menu_theme` | the theme's name (`lagoon`, `kelp_forest`...) | Which themes are worn (and sold). |
| `night_mode` | `day`, `night`, `auto` (like the phone) | Whether the night versions are used. |
| `club_member` | `yes`, `no` | Captain's Club members versus the rest. |

## Events from other features

Other features log their own events through the same facade (daily loop, social, game feel).
Add a section for each here, in the same format, when they land: the test lists every literal
`Analytics.event("...")` name it finds in `app/src/main/kotlin`.
