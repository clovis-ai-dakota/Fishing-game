# Deep Line — a fishing collection game for Android

An original fishing mini-game for Android, written in Kotlin with Jetpack Compose.
All art is made in code (no image or model assets): the game draws in real-time 3D with OpenGL
ES 3.0, in a cel-shaded anime style, with a 2D renderer as the fallback. The player's boat is a
little fishing trawler (raked bow, wheelhouse with framed windows and a life ring, fenders, rails,
a searchlight), fishing with a graphite rod, a reel that turns as the line runs, and a pale
monofilament line; the 2D fallback draws the same boat.

## How it plays

- **Steering.** Drag anywhere: a ring marks your finger and the hook glides onto it and follows
  it (speed proportional to the gap, capped by Rope Agility). Lifting the finger holds the hook.
- **Descent.** The hook sinks from the boat. Each bump uses up one of **4 bumps**: touching a
  fish's **head** (brushing its body or tail is free) or touching a rock, driftwood or jelly
  cluster anywhere. The first **50 m are bump-free**, so a cast that lands on a fish costs nothing. After the 4th
  bump, or at the end of the rope, the hook reverses direction. Bumping fish on the way down
  therefore pulls the line up early.
- **Ascent.** On the way up, nudge a fish's **head** with the hook to catch it. Touching its
  body or tail does nothing. Fish keep being hooked until the rope's **max weight** is reached.
- **Scoring.** Points = species base points × depth multiplier (1× at the surface, +1× per
  100 m). **Sparkling** fish (gold outline and glints) are worth 3× more.
- **Lantern.** Below 300 m the water goes dark and you only see a small circle around the hook.
  A Lantern (toggle it on before casting; one is used per cast) widens the lit circle from
  6 m to 18.2 m around the hook.
- **Fast reel.** On the way up, from the moment all the bumps are used (or once nothing more
  can fit on the line: the room left is smaller than the lightest species living above the
  hook, from the catalogue, so it reveals nothing about the water), a **Reel in fast** button
  hauls the hook to the boat in at most 1.5 s wherever it is (less when shallow). Nothing more
  is caught or counted after that. Normal and Hard rivals use it when their line is full.
- **The deep is fair.** In the dark (below 300 m) obstacles thin out (one every 30 m on average
  instead of 22 m) and the hook sinks a little slower (17 m/s instead of 18 m/s). Tuned with a
  human-like "good player" model (it only sees the screen, or the lit circle in the dark, and
  reacts in 0.25 s): with a full rope it reaches the bottom in about **a third of casts
  without a Lantern** (a casual player about 1 in 6, anyone with a Lantern about 95%).
  `DifficultyTest` keeps it there.

### Navigation

Four tabs in a bottom navigation bar (Material 3: 3 to 5 top-level destinations, always
labelled, in the thumb zone), so every main area is one tap away:

- **Fish**: the hub. One dominant **Cast line** button at the bottom with the Lantern switch next
  to it, your rope's reach drawn over the depth zones, Dex and milestone progress. The top bar
  holds your points, a trophy (with a badge when a milestone can be claimed) that opens
  Milestones, and **?** to replay the tour.
- **Dex**: the collection (see below).
- **Shop**: rope upgrades and downgrades (points only), then tackle, style, passes and the
  aquarium, each with a euro price and a points price (see below). A dot on the tab means
  something is waiting there (a Club gift, season rewards, the starter pack offer).
- **Battle**: battle setup and the way online.

Casts, battles, the online lobby, Milestones and Dex pages are full screens without the bar.
System back goes from any tab to Fish, then leaves the app.

### Dex

A numbered card for every species (#001 to #200), in a dense grid with zone-coloured frames,
filters by zone (and "caught only"), and a completion ring. A species is **Seen** once it swims
within 10 m of your hook (`GameConfig.SEEN_DISTANCE_M`; in dark water it must also be inside the
lit circle): its card shows a silhouette in its own colour, its name and depths. **Caught**
fills in its weight, base points, rarity stars and your records. Unknown species are a dark
silhouette with "???" and their depths. Tap a card for its page, and swipe to flip through the
Dex. Seen fish are only a log feature: not scored and not sent online.

Once found, **Secrets** and **Skins** sections appear next to Species. The Secrets section has a
card for every secret: found ones show their name and picture and open their explanation; locked
ones show only their number, tier and discovery reward ("a legendary skin set" for tier 5, never
the set's name). The Skins section is a library of every legendary set, with an animated preview:
tap a tile to wear it, tap again to go back to the classic look. Locked tiles show only their slot.

### Fishing secrets (spoilers)

Hidden mechanics, explained in game the first time they happen (with a "show this again next
time?" question) and then listed in the Dex's **Secrets** section. The tour hints that secrets
exist but never reveals them.

Every secret adds a **bonus to the cast every time** it's pulled off (solo and in battles, for
the computer rival too; online, the server recomputes it from the cast) and gives a **one-time
discovery reward**. Cast-wide multipliers add up rather than compound. Numbers live in
`core/.../Secrets.kt`.

| Tier | Secret | Trigger | Each time | Discovery reward |
| --- | --- | --- | --- | --- |
| 1 | The last bump hooks what it hits | The 4th bump hooks the fish whose head it hit (even over the weight limit) or snags the object: the only way to land rocks (200), driftwood (100) and jellies (150 base, × depth) | the hooked catch | — |
| 1 | Free pass | 3 fish heads passed through in the bump-free first 50 m | +50 | 250 |
| 1 | Schooling | 3+ fish of one species in a cast | +100 | 300 |
| 1 | Surface skim | A fish caught on the way up shallower than 10 m | +50 | 300 |
| 1 | Zen cast | No steering at all, and 3+ catches | +150 | 400 |
| 2 | Pinball | All 4 bumps within 2 seconds | +200 | 750 + 1 Lantern |
| 2 | Second encounter | On the way up, catch a fish you bumped on the way down | that fish ×2 | 750 + 1 Lantern |
| 2 | Clean drop | Reach the end of a rope of 300 m+ with no bumps | cast ×1.5 | 1,000 + 1 Lantern |
| 2 | Rock bottom | The last bump snags an object within 2 m of the rope's end | the snag ×2 | 1,000 + 1 Lantern |
| 2 | Buzzer beater | Timed battle: start a cast with under 15 s on the clock and land something | +300 | 800 + 1 Lantern |
| 3 | Corner pocket | On the way up, hook a fish within 0.3 s of it turning at the edge | +300 | 4,000 + 2 Lanterns |
| 3 | Grand tour | The 4 bumps hit a fish head, a rock, driftwood and a jelly | last bump's catch ×2 | 5,000 + 2 Lanterns |
| 3 | Sparkle snag | The last bump hooks a Sparkling fish | that fish ×2 (×6 with Sparkling) | 5,000 + 2 Lanterns |
| 3 | Ladder | 4+ fish caught on the way up in strictly increasing weight | cast ×1.5 | 4,000 + 2 Lanterns |
| 3 | Perfect load | Surface exactly at the weight limit (±0.05 kg), 5+ catches, the last one 1.5 kg+ | cast ×1.5 | 5,000 + 2 Lanterns |
| 3 | Moonlit angler | Without a Lantern, catch a glowing fish below 300 m on the way up | that fish ×3 | 6,000 + 3 Lanterns |
| 3 | Shutout | Win Best of 5 against the Hard rival 3-0 | +5,000 (profile points, each time) | 6,000 + 3 Lanterns |
| 4 | While it flees | On the way up, catch a fish you bumped while it is still fleeing | that fish ×4 | 18,000 + 4 Lanterns |
| 4 | Blind drop | Without a Lantern, reach the end of a 500 m+ rope with no bumps | cast ×2 | 22,500 + 4 Lanterns |
| 4 | Zone sweep | A fish from each of the 4 depth zones in one cast | cast ×2 | 24,000 + 5 Lanterns |
| 4 | Grand slam | Snag an object with the last bump and catch 8+ fish on the way up | cast ×1.5 | 18,000 + 4 Lanterns |
| 4 | Double header | Two fish hooked at the very same instant | both ×3 | 21,000 + 4 Lanterns |
| 4 | Crowd surfer | With a full line (instead of the fast reel), brush 50 different fish on the way up | cast ×2 | 22,500 + 5 Lanterns |
| 5 | David and Goliath | The last bump hooks the Pale Leviathan (24 kg) on a rope weaker than it | leviathan ×3 | 45,000 + 6 Lanterns + Giant-slayer set |
| 5 | Perfect cast | Rock bottom + Perfect load + a Sparkling catch on the way up, in one cast | cast ×3 | 60,000 + 8 Lanterns + Prismatic set |
| 5 | Master angler | Every species in the fish log, including the Deep Sovereign | — | 75,000 + 10 Lanterns + Sovereign set |

**The Deep Sovereign** (species #200) lives only at 600 to 620 m, appears in 5% of casts that
reach that deep, and can only be landed by the last bump (a normal head nudge on the way up does
nothing). It is worth 2,500 base points (× depth) and glows in the dark.

Online, the server recomputes every bonus from the reported cast and checks the facts they rely
on: what each bump hit must match the bumps used and the last-bump hook, "bumped then caught"
fish need fish-head bumps, catches come up deepest first and in time order (so a Ladder or a
Double header can't be faked by reordering), bump times and free passes must be consistent, the
Deep Sovereign must come from the last bump, and the rope and battle clock come from the server's
own records. Steering ("Zen cast") and "turned at the edge" can't be proven without replaying the
cast, so they are bounded but trusted.

### Skins

The Dex's **Skins** section is always there; tapping the boat at the top of the Fish tab opens it.
It has an animated preview of what you wear, then:

- **Your boat**: design your own in **Customise boat**: hull and bottom colours, a hull pattern,
  trim, cabin, sails (triangle, square, twin), a flag (pennant, swallowtail, banner) and an
  emblem, from a palette of 24 colours. The editor is free, but most options unlock by playing:
  some are bought once with points (from your balance: milestones keep counting), others come
  with progress (species caught, a fish from a deeper zone, milestones claimed). The default
  boat is entirely free. The unlock table is `core/.../BoatParts.kt`. Save, and your boat is worn.
- **Shop boats**: premade boats and shop-only options (paints such as flames, metallic or neon,
  emblems, sail prints) are bought on the Shop's Style page and then worn or edited here.
- **Title badge and Dex cards**: the frame of your title badge (gold, or rainbow, ocean, ember,
  the Club's aurora, season, collector and event frames) and the style of your caught Dex cards.
- **Shop gear**: hooks, lines and trails bought in the Shop, worn per slot like legendary skins.
- **Atmosphere**: Family (the default) is harmless near the surface and gets spookier with
  depth. Reaching the Abyssal Floor unlocks **Nightmare**, a frightening look for the deep that
  shows exactly as much as before.
- **Legendary skins**: tier 5 secrets each unlock a full set of five cosmetics, chosen per slot.

| Set (secret) | Hook | Line | Boat | Bubble trail | Title |
| --- | --- | --- | --- | --- | --- |
| Giant-slayer (David and Goliath) | Leviathan bone, pulsing cyan runes | Giant's braid with a running shimmer | Leviathan pennant, dark hull, cyan trim | Tide trail | Giant-slayer |
| Prismatic (Perfect cast) | Prism hook cycling colours | Rainbow line | Prism sail, rainbow trim | Prism trail | Perfectionist |
| Sovereign (Master angler) | Crown hook in gold | Golden line with glints | Royal boat: purple crowned sail, gold trim | Golden trail | Master angler |

The title shows under your name on the Fish tab and in battles (online opponents see it too).
Skins are purely visual: same hook size and hitbox, and all the effects are drawn under the
darkness layer and only around your own gear, so they never light up dark water or reveal fish.

### Themes

The menus sit on a living sea scene, drawn in the game's style: a sky band with the sun or the
moon, a moving waterline, light rays or moonlight, bubbles or plankton, a far shoal, and each
theme's own decor. **Lagoon** is the default (free); every theme has a **day and a night version**
(Day by default, Night, or like the phone's dark mode), chosen with the palette button at the top
of the Fish tab. More themes are in the Shop (Style page, or the Themes screen), for euros or
points, priced by how rich their scene is:

| Theme | Scene | Price |
| --- | --- | --- |
| Lagoon | Turquoise water, rays, bubbles, seagrass and starfish | free |
| Kelp Forest, Sandbar | A palette with one simple scene (swaying kelp; sandy shallows with caustics) | €0.50 |
| Coral Reef, Polar Waters | A scene with its own decor (corals and reef fish; ice floes and snow) | €0.99 |
| Sunset Harbor, Koi Pond, Treasure Cove | Fully animated scenes (sunset, boats and gulls; koi and lily pads; coins glinting by a chest) | €1.99 |
| Glow Tide | The most elaborate: glowing jellyfish, plankton and a deep glow | €4.99 |
| Aurora | Northern lights, for Captain's Club members | Club |
| Every theme | All the themes for sale | €9.99 |

Themes only change the menus: a cast's water always looks the same.

### Shop

Every item can be bought **with euros or with points**. A points price is fishing time: about
**45 minutes of a good player's fishing per euro** at your rope's reach
(`GameConfig.MINUTES_PER_EURO` × `POINTS_PER_MINUTE_BY_LENGTH_LEVEL`), and **1 hour per euro for
Lantern packs**, a key item for progress. Bigger purchases give more: +20% at €4.99, +40% at €9.99.
Paying only saves time: nothing is sold for euros that points can't buy. Points purchases ask
for a confirmation. All prices and contents are in `core/.../Shop.kt`.

| Page | What's there | Prices |
| --- | --- | --- |
| Rope | Length, Strength and Agility upgrades and downgrades | points only |
| Tackle | Lantern, Rare fish detector, Lucky charm, Sparkle lure: one with points, or packs sized to their price | €0.50 to €4.99 |
| Style | Premade boats, complete sets (cheaper than their pieces), paints, emblems, sail prints, hooks, lines, trails, title badges, Dex card styles, app themes | €0.50 to €9.99 |
| Passes | Season pass, collector's edition, weekend event pass, Captain's Club (monthly), starter pack (one time, from the 3rd session, 72 h), founder's pack | €1.99 to €9.99 |
| Aquarium | Decor and a bigger tank for the aquarium of caught fish | €0.50 to €4.99 |

- **Rare fish detector**: for one cast, marks species you haven't caught yet within 25 m of the
  hook, even in the dark. Like the Lantern it only changes what you see, so both are allowed in
  battles (anyone can get them); the charm and the lure are solo only.
- **Seasons** (6 weeks, `core/.../Seasons.kt`): every solo cast earns season XP (6 per cast, 1 per
  fish, 10 per secret) towards 20 tiers. The free track has tackle and a little points; the season
  pass adds a premium track (a season badge, the season boat at tier 10, lots of tackle); the
  collector's edition is the pass plus 10 tiers, the boat and a badge straight away.
- **Weekend events** (Friday 18:00 to Monday 06:00 UTC): one fishing goal with a reward; the event
  pass doubles progress and adds a bigger reward and an event badge.
- **Captain's Club**: the Aurora paint and a Club badge frame while a member, cloud save, and a
  free Lantern pouch every month.
- **Gifts**: after an online battle, send your opponent tackle or looks (euros or points). Gifts
  arrive when the game or the Shop opens; something they own already becomes its points price.
- **Aquarium** (Dex > Aquarium): caught fish swim among your decor; pick who's in the tank.

Payments: release builds use **Google Play Billing** (`app/.../billing/PlayStore.kt`); create
in-app products with the shop's item ids (tackle packs and seasonal items consumable, the rest
kept), a `captains_club` subscription, and `gift_<id>` consumables for gifts. Debug builds use the
in-app **test store**: buying shows a dialog where you approve, cancel, fail or leave the payment
pending (pending ones clear with Restore purchases). Build with `-PdeeplinePlayBilling=true` to
try Play billing in a debug build.

### Daily loop

A daily card on the Fish tab (and the Daily screen it opens) holds the day's reward, the streak
and today's challenges. Everything resets at the phone's local midnight. Research and decisions:
`docs/research/daily-loop.md`; rules in `core/.../Daily.kt`, `Migrations.kt` and `Reminders.kt`,
numbers in `GameConfig` (the calendar table is in `Daily.kt`).

- **Daily reward**: claim once a day from a 7-day calendar: points on days 1, 3 and 5, a rare fish
  detector, a Lucky charm and a Sparkle lure on days 2, 4 and 6, and on day 7 more points, a
  Lantern and a streak shield. Points are "casts' worth" at your rope's reach
  (`GameConfig.POINTS_PER_CAST_BY_LENGTH_LEVEL`), spendable but not counted for milestones.
- **Forgiving streak**: the calendar never goes backwards (a missed day only pauses it). The
  streak counts days in a row; a **streak shield** covers a missed day by itself (you start with
  one, day 7 brings another, two at most). Without enough shields the streak starts over at 1;
  nothing earned is lost. A clock set back or a flight west can't claim twice.
- **Daily challenges**: one easy, one medium and one hard a day, from the day's seed and matched to
  your rope (zones it reaches, species within reach that it can lift and that you've seen): cast a
  few times, catch fish (from a zone, a given species, Sparkling ones, a new species), reach a
  depth, score points, land kilograms, or land many fish in one cast. They pay tackle (detector,
  Sparkle lure, Lucky charm) and, for the hard one, points. One free swap a day. Finished but
  unclaimed challenges are collected for you at midnight. Only solo casts count: battle catches
  never count outside the battle.
- **Migrations**: every week from Tuesday 00:00 UTC for 48 hours (between the weekend events), two
  less common species, one from the upper water and one from the deep, spawn as often as the most
  common fish where they live. A banner shows them on the Fish tab and in the Dex (unknown species
  stay "???"), and the Daily screen shows the next one. Solo only: battles fish shared seeded
  water, so the online rules never change.
- **Reminders** (local notifications, no push server): offered once, right after your first daily
  reward, with a short explainer and then Android's permission dialog; Settings > Notifications
  has a switch for all of them and one per kind (daily reward ready, streak at risk, migrations and
  events, each its own Android channel). At most one a day, never between 21:00 and 09:00 local
  time, nothing once today's reward is claimed; the streak reminder comes in the evening instead
  of the daily one. Tapping one opens the Daily screen, the Fish tab or the Shop's Passes page.
  A WorkManager check runs every 2 hours (`ReminderWorker`).

### Guided tour

On first launch a guided tour walks through the game: it highlights each part of the app and
explains the tabs, casting, steering, bumps, heads, weight, points, the Shop, Lanterns, the Dex,
milestones and battles. It can be skipped at any time; when it closes, it asks whether to show
it again next time. The **?** button at the top of the Fish tab replays it. When new features add tour
steps, returning players get a short "What's new" tour of just those steps
(`app/.../ui/tour/Tour.kt`).

### Around the casts

There is no time limit: it is an ongoing collection game. Explore deeper zones as your rope
improves and try to fill the Dex.

- **Species.** 200 species across 4 depth zones (Sunlit Shallows, Twilight Reef, Midnight
  Trench, Abyssal Floor): 16 hand-made ones, 183 generated from a fixed seed
  (`core/.../SpeciesGenerator.kt`) and the legendary Deep Sovereign. The Dex records count,
  Sparkling count, deepest catch and best score per species.
- **Points and milestones.** Every catch earns points. Milestones are based on the lifetime
  total earned, so spending points never loses milestone progress. Milestones reward bonus
  points and Lanterns.
- **Rope upgrades** (paid with points): Length (max depth, which unlocks deeper zones),
  Strength (max weight) and Agility (steering speed), 10 levels each. Any level can be
  **downgraded** for a full refund of what it cost (lifetime points earned, and so milestones,
  are unaffected). The Deep Sovereign's water needs the maximum rope (620 m).

### Points: earning and spending

Deeper casts earn far more (about 140 points per cast with the starting rope, 18,000 with a
full one), so prices follow income. Tuned with a career simulation of a good player
(`EconomyTest`, about three casts a day, claiming the season's free track and the daily loop):
the Midnight Trench takes about 23 casts, the Abyssal Floor about 150 and a full rope about 260
(all three stats maxed around cast 290, roughly 5 hours of play).

- **Rope upgrades** follow `RopeStat.COST_CURVE` (200 points for the first level, 225,000 for
  the last), weighted per stat; downgrades refund in full. The levels from 5 up were raised
  (about +20%) when the daily loop added income.
- **Tackle** (Shop): one-cast items switched on before a cast. **Lantern** (lights the dark) and
  **Rare fish detector** (marks uncaught species nearby), both allowed in battles, **Lucky charm**
  (one extra bump on the way down) and **Sparkle lure** (3× as many Sparkling fish), solo only.
  Their prices grow with the rope's reach (`GameConfig.TACKLE_PRICE_BY_LENGTH_LEVEL`), so they stay
  a real decision late in the game.
- **The shop**: everything else in the Shop can be bought with points too (see above).
- **Seasons** and the **daily loop**: the season's free track, daily rewards, challenges and
  migration water are all part of the career simulation. The daily loop pays about 11% of what a
  career earns; without it the same career takes about 24, 200 and 316 casts.
- **Boat parts** cost 6× their table price.
- **Wagers** on battles against the computer (1k to 100k points): a win pays the stake back
  ×1.2 (Easy), ×1.6 (Normal) or ×2.4 (Hard), a draw refunds it, a loss or forfeit keeps it.
- **Milestones** are spread over a whole career (15 of them, up to 4 million points earned).
- Discovery rewards of tier 3 to 5 secrets were raised (×2 to ×3): they're usually found
  later, when casts earn much more.

### 1v1 battles

Against the computer (Easy / Normal / Hard), a friend on the same device (pass and play), or
**online**. Both players fish **the same water** for the same cast number (or round), so skill
decides. Offline both use the device's current rope; online both use a standard tournament
rope (level 5 in every stat). Lanterns and rare fish detectors can be switched on for your casts
(each uses one from your own stock); the Lucky charm and the Sparkle lure are solo only. Battle
catches don't count towards points or the fish log. The record against the computer is saved.

| Mode | Rules |
| --- | --- |
| Best of 5 | One cast each per round, higher score takes the round, first to 3 rounds wins. Tied rounds count for nobody (capped at 9 rounds, then the score decides). |
| 3-Minute Rush | 3 minutes of fishing each; only the single best cast counts. A cast still running when time is up ends on the spot and scores the fish already on the line. Pausing stops the clock. |
| Heavy Haul | 3 casts each; most total kilograms wins. |
| Species Hunt | 3 casts each; most different species wins, total points break ties. |
| Deep Dive | 3 casts each; the single deepest catch wins, total points break ties. |

#### Online

- **Room code:** one player creates a room and shares the 5-character code; the other joins.
- **Quick match:** you are paired with the next player who picks the same mode.
- Both players fish **at the same time** on their own phones and see the opponent's live depth
  and score. In Best of 5 the next round starts once both have finished the current one.
- The server is authoritative: it checks every cast (species exist, live at that depth, fit
  within the tournament rope's length and weight limit, valid turn) and **recomputes the
  points itself**. Leaving or losing the connection mid-match forfeits it.
- Not handled yet: reconnecting to a match after a dropped connection, accounts or rankings,
  and full anti-cheat (a modified client could still report fish it never actually hooked).

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
| Lantern | Start with 1; earned from milestones, seasons and events, or bought in the Shop. |
| Shop exchange rate | 45 min of a good player's fishing per euro (Lantern packs 60 min), +20% at €4.99, +40% at €9.99. |
| Gifts and cloud saves | The server trusts the app that a gift was paid for (it checks the item can be given); cloud save is a Club perk checked by the app. |
| Online rope | Same tournament rope for both players (level 5 in every stat), since each phone has its own upgrades. |
| Online pause | Pausing stops your own clock only; the opponent keeps fishing. |
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
        Progress.kt        points, milestones, upgrades, fish log, Dex states (seen / caught)
        Battle.kt          1v1 battle modes, turns, scoring
        Bot.kt             computer rival
        Persistence.kt     save/load (key=value text)
        Shop.kt            the shop: items, euro and points prices, what buying gives
        Seasons.kt         seasons, the season track and weekend events
        Daily.kt           local days, the login streak and reward calendar, daily challenges
        Migrations.kt      weekly migrations (spawn boosts for solo casts)
        Reminders.kt       when a reminder is due (at most one a day, quiet hours)
        Showcase.kt        title badge frames, Dex card styles, the aquarium
        Themes.kt          menu themes and the night mode
        Tackle.kt          one-cast tackle (Lantern, detector, charm, lure)
        PlayerId.kt        public id, secret and recovery code (gifts, cloud save)
        online/            battle protocol (JSON messages), cast validation, client-side match mirror,
                           AccountApi.kt (the gift and cloud save HTTP API)
server/ Ktor battle server: WebSocket battles (room codes, quick match, authoritative) and the
        account API (Accounts.kt: gifts and cloud saves in one JSON file per player) + tests
app/    Android app (Jetpack Compose)
        GameViewModel.kt   game state, persistence via SharedPreferences, online flow
        OnlineConnection.kt WebSocket client (Ktor + OkHttp)
        AccountClient.kt   gifts and cloud save client
        billing/           Store: Google Play Billing (PlayStore) and the debug test store
        ui/CastScreen.kt   game loop, HUD, pause, results
        ui/render3d/       3D renderer: procedural meshes, scene builder, GL backend (gl/)
        ui/CastRenderer.kt 2D scene (fallback) and the 2D overlay drawn over the 3D view
        ui/MainScaffold.kt the four tabs (bottom navigation bar)
        ui/dex/            the Dex: species grid and pages, secrets, skins library, FishPortrait
        ui/boat/           boat editor; ui/BoatArt.kt draws boats in 2D
        ui/shop/           the Shop tab, seasons and events, the aquarium, themes, gifts and the account
        daily/             the daily loop's controller, reminder channels, worker and scheduling
        ui/daily/          the Fish tab's daily card, the Daily screen, migration banners
        ui/theme/          menu themes: palettes (day and night) and the animated sea scene
        ui/GameIcons.kt    icons drawn in code
        ui/*Screen.kt      Fish tab (HomeScreen), milestones, battle setup / hub
```

## Building

Requirements: JDK 17+ (JDK 21 for the app UI tests: Robolectric needs it for Android 16), Android SDK (API 36).

```bash
./gradlew :core:test            # game-logic unit tests (no Android SDK needed)
./gradlew :app:assembleDebug    # APK at app/build/outputs/apk/debug/
./gradlew :app:installDebug     # install on a connected device / emulator
```

### Tests

```bash
./gradlew :core:test :server:test :app:testDebugUnitTest
```

- `core`: game rules, battles, the computer rival and the online protocol.
- `server`: real WebSocket matches against the battle server.
- `app` 3D: `Render3dTest` checks the camera mapping, every mesh's winding, that fish bodies
  match their hitboxes and that hook skins keep the hook's size. `Render3dExportTest` exports
  frames that `node tools/render3d-preview/render.cjs` draws with WebGL2 (headless Chromium) and
  the app's own shaders, into `app/build/render3d/*.png`.
- `app`: Robolectric UI tests that run the real app on the JVM. They tap through every
  screen, play casts and battles, render the dark zone with and without a Lantern, and play a
  full online match against the real server (started as a separate process). Screenshots are
  written to `app/build/screenshots/`; CI uploads them as the `deep-line-screenshots` artifact.

### Battle server

```bash
./gradlew :server:test           # server tests (real WebSocket matches)
./gradlew :server:run            # runs on http://localhost:8080 (PORT env var to change)
docker build -t deepline-server . && docker run -p 8080:8080 deepline-server
```

Gifts and cloud saves are stored as one JSON file per player in `data/accounts`
(`DEEPLINE_DATA_DIR` to change it; keep it on a persistent volume). The HTTP API is described in
`core/.../online/AccountApi.kt`.

Any host that runs a Docker container or a JVM works (for example Fly.io, Render, Railway or a
VPS). Players connect to `ws://<host>:8080/battle`, or `wss://<host>/battle` behind HTTPS.

The app's default server address is `ws://10.0.2.2:8080/battle`, which is the development
machine as seen from the Android emulator. To ship a build pointing at your deployed server:

```bash
./gradlew :app:assembleDebug -PdeeplineServerUrl=wss://your-host.example.com/battle
```

Players can also change the address in the app (Online battle → Server settings).

The `:app` module is only included when an Android SDK is found (`ANDROID_HOME`,
`ANDROID_SDK_ROOT`, or `sdk.dir` in `local.properties`), so `:core` can be built anywhere.

CI (`.github/workflows/android.yml`) runs the tests and builds a debug APK on every push. The
APK can be downloaded from the workflow run's artifacts (`deep-line-debug-apk`).
