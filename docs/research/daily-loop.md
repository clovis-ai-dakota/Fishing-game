# Daily loop and live ops: research and decisions

What the best casual and live games do for login streaks, reward calendars, daily challenges,
reminders and limited-time events, and what Deep Line does as a result. Numbers live in
`core/.../GameConfig.kt` (and the calendar table in `core/.../Daily.kt`); this note explains why.

## 1. Login streaks and reward calendars

### What leaders do

- **Duolingo** made the streak (consecutive days with at least one lesson) its core retention
  feature and tests it constantly (reported: hundreds of experiments over two years, 9 million
  learners with a year-long streak). The day ends at **local midnight** on the device.
  A **Streak Freeze** protects one missed day and is used automatically; two missed days in a row
  break the streak unless more freezes are equipped. **Streak Repair** restores a lost streak for
  a short time after it breaks. In 2026 Duolingo even ran a "Streak Revival" event to give back
  long lost streaks, saying that losing a streak to events outside a learner's control is
  demotivating. ([Android Authority](https://www.androidauthority.com/duolingo-revive-broken-streak-event-3673004/),
  [PocketGamer.biz](https://www.pocketgamer.biz/duolingo-is-restoring-lost-streaks-for-a-limited-time-aiming-to-recover-lapsed-users/),
  [The Audiencers](https://theaudiencers.com/subscription-models-live-off-habits-lessons-from-duolingos-retention-success/))
- **Paid repairs backfire.** Snapchat started charging to restore Snapstreaks in 2023 and met a
  clear backlash. Streaks lean on loss aversion (losses weigh about twice as much as gains), so
  making players pay to undo a loss reads as exploitative.
  ([PiunikaWeb](https://piunikaweb.com/2023/05/15/snapchat-charging-users-to-restore-streaks-met-with-backlash/),
  [Snap newsroom](https://newsroom.snap.com/keep-the-streak-with-restore))
- **Reward calendars** in casual games are usually **7-day cycles** with one reward per day and a
  featured, clearly better **day 7**. They work by showing what you get today, what comes next and
  when it unlocks. The main design choice is what a missed day does: reset to day 1 (punishing),
  keep progress but lose the missed slot, or let the player pick up where they left off.
  Unity's Daily Rewards solution lets a player still claim after missing a day.
  ([Appcharge reward calendars](https://docs.appcharge.com/guides/webstore/offers/reward-calendars/about-reward-calendars),
  [Unity Daily Rewards](https://docs.unity.com/en-us/services/solutions/daily-rewards),
  [Game Wisdom, "XP for logging in"](https://game-wisdom.com/general/xp-logging-age-daily-gaming-habits))
- Login bonuses should **add to the game, not be the reason to open it**: they're a nudge
  towards play, worth a modest share of what play earns.
  ([Game Wisdom](https://game-wisdom.com/general/xp-logging-age-daily-gaming-habits))
- **Time zones** are where streaks go wrong: players lose streaks when travelling or when the app
  and the phone disagree on the day. ([Fitbit community report](https://community.fitbit.com/t5/Luxe/Luxe-won-t-update-the-time-after-traveling/td-p/5376592),
  [Duolingo guides](https://duolingoguides.com/how-to-fix-duolingo-not-counting-streak/))

### Decisions for Deep Line

- **One claim per local calendar day**, at the phone's local midnight (like Duolingo). A day is
  `floor((epochMs + zoneOffset) / 24 h)`, computed in pure Kotlin (`LocalDays`), so it works on
  every Android version without `java.time` and is testable with any zone.
- **A 7-day reward calendar** (`DailyRewards.calendar`): points on days 1, 3 and 5, one piece of
  tackle on days 2, 4 and 6, and a featured day 7 (Lanterns, more points and a streak shield).
  Points are "minutes of a good player's fishing at your rope's reach", the same unit as the
  shop, seasons and weekend events, so the rewards stay worth having all career long.
- **Forgiving by design.** The calendar **never goes backwards**: a missed day only pauses it,
  and the next claim gives the next reward. The **streak** (a count of days in a row) is
  protected by **streak shields** (our Streak Freeze): new players start with one, day 7 of the
  calendar gives another, up to two; one is used automatically per missed day. Without enough
  shields the streak starts again at 1, but nothing already earned is lost. There is **no paid
  repair**: nothing about streaks is sold.
- **Clock safety.** A claim needs a day later than the last claimed one, so setting the clock
  back (or flying west over a date change) can't claim twice; challenges also only move
  forwards. Setting the clock forward can't be told apart from time passing in an offline game;
  rewards are modest, so it's accepted (documented, not fought).

## 2. Daily quests and challenges

### What leaders do

- **Hearthstone**: a quest log of **three** quests, one new quest a day, **one free reroll a day**
  that doesn't accumulate. Rerolling a hard quest always gives an easy one; an easy one gives
  easy or hard at 50/50 (players wanted a way out of quests they couldn't do).
  ([Hearthstone wiki, daily quest](https://hearthstone.wiki.gg/wiki/Daily_quest),
  [2013-2020 system](https://hearthstone.wiki.gg/wiki/Quest/2013-2020_system),
  [Out of Games](https://outof.games/news/1087-a-change-to-daily-quest-logic-in-hearthstones-latest-patch-is-now-confirmed/))
- Across mobile games the pattern is a **small daily count (3 to 6)**, a **limited reroll**,
  a **fixed reset time**, and **tiered difficulty**. Unclaimed quests are often lost at reset,
  which players resent; rolling reset timers that drift with the time of the last login ("time
  creep") are a known pain point.
  ([Solitaire Social](https://support.solitairesocial.com/hc/en-us/articles/11856849592605),
  [Skyweaver](https://support.skyweaver.net/en/article/quests-2oq8di/),
  [Idle Clans wiki](https://wiki.idleclans.com/index.php/Quests),
  [MTG Arena feedback](https://feedback.wizards.com/forums/918667-mtg-arena-bugs-product-suggestions/suggestions/50750660-critical-bug-daily-quest-timer-constantly-resets))
- Session design matters more than the quest list: decide how many sessions a day you expect and
  make each one end well. ([Game Developer, AFK Arena](https://gamedeveloper.com/design/flexible-time-session-design-in-afk-arena))

### Decisions for Deep Line

- **Three challenges a day: one easy, one medium, one hard**, generated from a **deterministic
  date seed** (the local day number) plus what the player can reach, so two anglers with the same
  rope see the same day's challenges.
- **Matched to the player**: zones the rope actually reaches, species that live within reach
  and fit under the rope's weight limit, depths and point targets scaled to the rope. A "new
  species" challenge only appears while uncaught species are within reach.
- **One free reroll a day** (it doesn't carry over), for a challenge that isn't claimed yet; the
  new one has the same difficulty and a different kind.
- **Fixed reset at local midnight**, with the streak (no time creep). **Forgiving reset**:
  challenges finished but not claimed are collected automatically when the day changes, and the
  player is told.
- **Only solo casts count**, like points, the fish log and the season track: battle catches don't
  count for anything outside the battle, pass-and-play and Easy rivals would otherwise farm
  them, and online casts use a tournament rope. (A "win a battle" challenge could drive players to
  battles later; it would need its own economy check.)
- **No challenge points at fishing secrets** (no "three of one species", "every zone in one cast",
  "no bumps"...): secrets stay hidden mechanics.

## 3. Reminders: local notifications

### What leaders do and Google recommends

- **Android 13+** needs the `POST_NOTIFICATIONS` runtime permission; new installs start with
  notifications **off**. Apps targeting 13+ choose when to ask: **in context, from a user
  action**, with an explanation of what the notifications are for ("transparency tends to
  increase opt-in"), after the player has had time to know the app. A **pre-permission screen**
  is recommended when `shouldShowRequestPermissionRationale()` is true. Check
  `areNotificationsEnabled()` before posting, and "use the permission responsibly": users see how
  many notifications an app sends and can revoke it any time.
  ([Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission),
  [Android 13 behaviour change](https://developer.android.com/about/versions/13/changes/notification-permission),
  [Top tips for adopting the notification permission](https://medium.com/androiddevelopers/top-tips-for-adopting-androids-notification-permission-bf69afd677b8))
- **Channels**: one per type of notification, so players can mute each kind in system settings;
  importance can't be changed by the app after creation; creating an existing channel again is a
  no-op, so it's safe at startup; offer a way to open the system settings.
  ([Create and manage notification channels](https://developer.android.com/develop/ui/views/notifications/channels))
- **Frequency**: Google Play's games team found that more notifications a day go with more
  opt-outs, and recommends dropping low-value ones. Industry guidance for games is a hard daily
  cap, **quiet hours** around 22:00 to 08:00 local time, and local-time-aware sends; most
  opt-outs come from poor relevance or frequency.
  ([Google Play Apps & Games, optimizing notifications in games](https://medium.com/googleplaydev/optimizing-notifications-in-games-5efd6ba89701),
  [MWM push best practices](https://mwm.ai/guides/push-notification-best-practices),
  [SuprSend on opt-out rates](https://www.suprsend.com/post/reduce-push-notification-opt-out-rates),
  [Girard Media on timing](https://girardmedia.com/blog/push-notification-timing-frequency-optimization))
- **Duolingo** sends one practice reminder a day and optimises its wording with a bandit; new
  messages work best and their novelty wears off, so it rotates them and counts a reminder as a
  success when a lesson follows within two hours.
  ([Yancey & Settles, KDD 2020](https://research.duolingo.com/papers/yancey.kdd20.pdf),
  [paper summary](https://paperswithcode.com/paper/a-sleeping-recovering-bandit-algorithm-for))
- **Deep links**: a notification must open the screen it talks about, not just the app.
- **Testing**: WorkManager's `work-testing` artifact (`WorkManagerTestInitHelper`,
  `SynchronousExecutor`, `TestDriver.setPeriodDelayMet`) runs scheduled work synchronously.
  ([Integration tests with WorkManager](https://developer.android.com/guide/background/testing/persistent/integration-testing))

### Decisions for Deep Line

- **Never at first launch.** The question comes at a meaningful moment: right after the
  **first daily reward claim** (when a reminder obviously helps), with a short in-app explainer
  (what we send, at most one a day, never at night, turn off any time). "Turn on" then shows the
  Android 13+ system dialog; "Not now" is respected and never asked again by itself (Settings has
  the switch).
- **Local only** (no push server): a WorkManager periodic check every couple of hours decides
  whether one reminder is due. Logic is a pure function in core (`DailyReminders.plan`), tested
  without Android.
- **Three channels**: daily reward ready, streak at risk, events and migrations; each also has an
  in-app switch in Settings > Notifications.
- **At most one notification a day**, **quiet hours 21:00 to 09:00 local time**, nothing once
  today's reward is claimed, the daily-reward reminder only if the game wasn't opened today, the
  streak reminder only in the evening for a streak of two days or more (it replaces the noon
  reminder rather than adding to it), an event reminder once per event. Texts rotate per day.
- **Deep links**: daily and streak reminders open the Daily screen, a migration opens the Fish
  tab with its banner, a weekend event opens the Shop's Passes page.
- Analytics: `notification_opt_in` / `notification_opt_out` (with where), `notification_open`
  (with the type), `notification_sent`.

## 4. Limited-time migrations

### What leaders do

- **Pokémon GO Community Day**: once a month one featured Pokémon spawns far more often for a
  short window, with extras (boosted shiny odds, an exclusive move). The window was cut from six
  to three hours in 2022 because fewer than 5% of players played longer than three hours; players
  still ask for flexibility (a 24-hour day where you pick your 3 hours).
  ([Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Community_Day),
  [Automaton](https://automaton-media.com/en/?p=10815),
  [Mein-MMO](https://mein-mmo.de/en/pokemon-go-trainers-demand-brilliant-change-to-community-days-that-will-probably-solve-all-time-problems,937282))
- **Animal Crossing: New Horizons**: every fish has months and hours when it can be caught, so the
  calendar itself makes collecting interesting and creates "catch it before it leaves" moments.
  ([GamesRadar](https://www.gamesradar.com/animal-crossing-new-horizons-fish/),
  [Nintendo Life](https://www.nintendolife.com/guides/animal-crossing-new-horizons-fish-complete-fish-list-fishing-tips-times-locations-and-prices))

### Decisions for Deep Line

- **A migration every week, Tuesday 00:00 to Thursday 00:00 UTC (48 hours)**, mid-week so it
  doesn't overlap the weekend events (Friday 18:00 to Monday 06:00 UTC). Our sessions are short
  and spread over the day, and players live in every time zone, so a two-day window covers at
  least one whole local day everywhere (the lesson from Community Day's 3-hour window).
- **Two featured species**: one from the upper water (Sunlit Shallows or Twilight Reef) so new
  anglers can join, and one from the deep (Midnight Trench or Abyssal Floor) for veterans. Both
  are among the less common species, so a migration is a real chance to fill the Dex. Their
  spawn weight is ×6 where they live (`GameConfig.MIGRATION_SPAWN_MULTIPLIER`); the number of
  fish, obstacles, scoring and rules are unchanged. The rotation is a fixed shuffle, so it's the
  same for everyone and known in advance ("next migration").
- **Shown** as a banner on the Fish tab and in the Dex (unknown species stay unknown: silhouette
  and "???" until seen), and on the Daily screen with the next one's date.
- **Solo only.** Battles fish water generated from a shared seed so both players see the same
  fish; online both phones generate it. Boosting would need the server to send the boost in
  `MatchStarted` and both clients to apply it identically; it's possible but not worth the risk
  now, so battles (and the online rules) never change.

## 5. Economy

Daily rewards and challenges are player income, so the career simulation (`EconomyTest`'s
`Career`, about three casts a day) claims the daily reward every day, plays the day's challenges
as they come (no rerolls, no tackle bought for them), claims what it completes, and fishes
migration water. Reward sizes and the rope's cost curve were re-tuned so the targets still hold.

What the simulation showed (seed 2, casts to the Midnight Trench / Abyssal Floor / full rope):

| Career | Midnight | Abyss | Full rope | All maxed |
| --- | --- | --- | --- | --- |
| Before the daily loop (season free track only) | 24 | 159 | 302 | 305 |
| First sizing (rewards of 0.3 to 1 cast's points a day, points for every challenge) | 20 | 96 | 167 | 168 |
| Smaller points, challenges paying mostly tackle | 23 | 123 | 221 | 234 |
| Same, rope levels 5 to 10 about 20% dearer (**shipped**) | 23 | 153 | 260 | 291 |
| Shipped prices without the daily loop | 24 | 202 | 316 | - |

Other seeds with the shipped numbers: Midnight 21 to 24, Abyss 124 to 159, full rope 229 to 268.

Why such a small income moved the pace so much: late in a career the good player buys a Lantern
for almost every dark cast, so what they save per cast (income minus Lanterns) is only about
2,000 points at rope level 9. A daily loop worth about 11% of a career's points (the shipped
sizing) more than doubles that margin. So the daily loop's points are kept small (a fraction of a
cast's points a day), its value is mostly tackle to try (detectors, Sparkle lures, Lucky charms,
which the simulation never uses), and the upper rope levels were made dearer to keep the targets.
