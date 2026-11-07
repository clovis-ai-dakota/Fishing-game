# Social and competition: research and decisions

Friends, weekly leaderboards, an asynchronous weekly tournament and clubs for Deep Line. This
note records what leading games do, what we chose, and why. Code: `core/.../core/social/`,
`server/.../SocialStore.kt` and `SocialRoutes.kt`, `app/.../social/` and `app/.../ui/social/`.

A caveat on sources: much of what games do is only documented by fan wikis and guides, which
disagree on details (limits, cooldowns) and go stale. Official help pages are cited where they
exist; the rest is treated as indicative.

## 1. Friend systems

**What leaders do**

- *Pokémon GO*: every trainer has a 12-digit Trainer Code (and a QR code). You send a request with
  the code and the other trainer accepts or declines it from their friend list. There is no
  search by username, so strangers can't add you without being given the code. Removing a friend
  is silent ("the person you unfriended will not be notified"). The list is capped (400 friends
  per Bulbapedia; Niantic's FAQ gives a higher cross-app figure); a full list must be trimmed
  before adding anyone.
- *Nintendo Switch*: a 12-digit friend code that can be reissued; requests must be accepted; the
  account has settings to turn friend requests off, a blocked-user list, and notification toggles.

**Patterns**: a short shareable code instead of name search; a request that the other side accepts
(never an instant add); silent decline and removal; caps on the list and on pending requests;
privacy controls (who can send requests, blocking).

**Decisions**

- The existing public player id (8 characters from an unambiguous alphabet, already shown to
  online opponents for gifts) *is* the friend code. Players can't be found by name.
- Requests need acceptance; asking someone who already asked you makes you friends at once.
  Decline and remove are silent and two-sided. Caps: 50 friends, 30 pending requests per player,
  20 requests sent per day (`GameConfig`).
- What friends see: the name the player uses online, their title badge, online status (seen by the
  server in the last 5 minutes, which costs nothing extra: every social call and every verified
  battle connection updates it), and this week's best cast, deepest catch and tournament score.
- Someone who merely sent you a request doesn't learn the name you chose (outgoing requests show a
  generated name): only accepting reveals it.
- **Challenge a friend**: the existing lobby already had room codes, so no protocol change was
  needed: the challenger creates a room, the app posts its code to the friend (it waits 10
  minutes), and the friend's Join button joins that room.
- Follow-ups: a block list and a "don't accept requests" switch (declining is silent and requests
  are rate-limited, which covers the worst cases for now).

## 2. Leaderboards

**What leaders do**

- *Google Play Games Services* makes daily, weekly and all-time versions of every leaderboard;
  weekly boards reset at a fixed moment (Saturday to Sunday midnight, UTC-7), a submitted score
  only replaces an entry it beats, and developers can hide players who submitted fraudulent scores.
- *Duolingo leagues*: a weekly board of 30 players grouped together, with promotion and demotion
  between ten tiers. Small cohorts make a top rank feel reachable (on a global board most players
  are hopelessly far from the top), and the weekly rhythm brings players back.
- Server-authoritative scores (Open Hexagon's leaderboard devlog, server-side validation write-ups):
  never trust a score the client sends. The strongest approach replays inputs on the server
  (deterministic seed + inputs); cheaper ones re-derive the score from facts and bound them,
  rate-limit submissions, and check top entries more carefully.

**Decisions**

- Three weekly boards: best single cast (points, secret bonuses included), deepest catch and rarest
  catch (rarest species, deeper catch breaks ties). Views: everyone (top 50), your league, and you
  and your friends; your own row and rank are always shown.
- **Reset: Monday 00:00 UTC** (`Weeks`), the same instant for the tournament and the clubs' goal,
  and the boundary of the existing seasons (season 1 started on Monday 5 January 2026, 00:00 UTC).
  The server simply starts a new week file, so the reset needs no job.
- **Server-authoritative**: only casts the server validated itself count, the tournament's attempts
  and online battle casts. Both use the standard tournament rope and the online rules, so the
  boards compare skill, not rope upgrades or purchases. Solo casts (any rope, Lucky charm, Sparkle
  lure) never count. Points are always recomputed; tournament catches are also checked against the
  week's water (see below), and battle casts are only ranked when they pass the same water check
  and come from a *verified* player (protocol 7 sends the secret with the battle Hello).
- **Leagues (lightweight)**: players are grouped by the order of their first score of the week,
  30 per league. No promotion or demotion yet (a follow-up once there are enough players).
- **Names on public boards**: strangers are shown with a name generated from their id ("Swift
  Marlin 7Q") and their title badge (cosmetics only); typed names are only shown to friends. Ids
  of strangers aren't sent at all. Public boards therefore never need moderating.

## 3. Asynchronous tournaments

**What leaders do**

- Seed-based skill tournaments (patent filings for skill-game operators) give every entrant the
  same seed for small fields, or draw from a pool, and limit the number of entries.
- *SimCity BuildIt*'s Contest of Mayors limits attempts per round, and cancelled or failed attempts
  still use one, so the limit means something. Rewards come at the end of each round.
- *Royal Match*: King's Cup (a 2-day, 50-player event) and the Royal League (groups of 20, rewards
  by rank band). *Fishing Clash*: clan matches reward the top places, and only players who
  scored.
- Reward design advice: band rewards by percentile, keep something for the middle and a small
  participation reward, avoid pay-to-win.
- Server-issued runs: the server issues a one-time run id and seed at the start, so the run's
  clock and seed are the server's.

**Decisions**

- One tournament per week, **the same water for everyone**: a seed per week (`Tournaments.seedOf`),
  salted by a server secret (`DEEPLINE_TOURNAMENT_SALT`) so the water can't be known in advance;
  the server only reveals the seed when a player starts an attempt.
- Online rules: the standard rope (level 5 in every stat), 4 bumps; Lanterns and detectors are
  allowed (they only change what you see, as in battles); no Lucky charm or Sparkle lure.
  Tournament catches don't count for points or the fish log, like battles.
- **5 attempts a week; starting one uses it** (quitting or losing the connection scores 0). Best
  score counts; ties go to whoever reached it first.
- **Validation**: the attempt must be sent within 15 minutes of starting, not faster than the cast
  lasted, and pass `CastValidation` (the battle rules); then `WaterCheck` regenerates the week's
  water and checks every catch is a distinct fish of that species at its exact depth (fish swim at
  a fixed depth) with the right Sparkle, and every snag a real object. A modified app can still pick
  which of the water's fish it claims, but can't invent fish. A rejected attempt stays at 0.
- **Rewards by final rank**, claimable for 4 weeks after the week ends: champion (the winner and
  the top 1%), top 10% (both win the new *Tournament* title badge frame), top 25%, top half, and a
  small reward for everyone who took part. They are deliberately smaller than a weekend event's
  reward (a core test checks it), so the economy's pacing (`EconomyTest`) holds.
- Claims use the gifts' pattern: the app grants the reward once (it remembers granted weeks in the
  save), then acknowledges it; an acknowledgement that failed is retried on the next visit.
- Follow-up: full replay validation (sending steering inputs and re-simulating on the server) would
  close the "pick the best fish" gap; casts are deterministic from the seed and inputs, so it's
  feasible, at the cost of bigger uploads and server CPU.

## 4. Clubs (built now, shipped later)

**What leaders do**

- *Clash Royale* clans: up to 50 members, four roles (Member, Elder, Co-leader, Leader); elders
  can kick members with a cooldown; an inactive leader is rotated out automatically to the
  longest-serving co-leader or elder.
- *Brawl Stars* clubs: up to 30 members, roles Member, Senior, Vice President and one President;
  clubs compete together in Club League.
- *Clash of Clans* Clan Games: members' points add up to a shared total that unlocks reward tiers;
  there is a per-player cap; points of a member who leaves stay with the clan; rewards are claimed
  for a few days after the event.
- Moderation: free-text names and chat need proactive filtering (Xbox treats player-entered names
  as user-generated content); hateful usernames are a known problem in popular games (ADL); kids'
  products use generated names (e.g. colour + animal) to avoid free text.

**Decisions**

- Join by a 6-character code; up to 30 members; one club per player.
- Two roles, owner and member. The owner can remove members (who can't rejoin for 24 hours), hand
  the club over and rename it. Anyone can leave; when the owner leaves, the longest-standing
  member takes over; the last member out closes the club. (Inactive-owner rotation is a
  follow-up.)
- **No chat, and no typed club names**: a name is picked among generated "adjective + noun"
  suggestions from fishing word lists. Members see each other's online names (as battle opponents
  already do).
- **Weekly goal**: fish caught together in solo casts, with three tiers (100, 300, 600 fish).
  Every member who helped that week gets each tier's reward (small: a few Lanterns, a detector,
  a little points). Fish are reported by the app (solo casts are played on the phone), so the
  server caps them at 400 per member per day: trust but bound, like gifts. Fish of a member who
  leaves stay in the club's total. Rewards can be claimed until the end of the following week.
- A club leaderboard (members by fish this week) and the week's most active clubs.
- Shipped behind `FeatureFlags.clubs` (see CLAUDE.md for how to ship it).

## 5. Abuse prevention and account deletion

- **Authentication**: every call carries the public id and the secret (the existing account
  model: the first use registers an id, the server keeps a hash).
- **Rate limits** (in memory, sliding windows): 60 social writes per player per minute, 600 calls
  per IP per minute, 20 friend requests and 10 club joins per player per day, 30 challenges per
  hour. Over the limit: HTTP 429.
- **Validation**: ids, room and club codes, name indexes and enums are checked; bodies over 32 KB
  are refused; profiles keep only a sanitized name, a title that is a real cosmetic and a real
  badge frame.
- **Names**: typed names are only shown to friends, club members and battle opponents; public
  boards use generated names; clubs use generated names. A word filter for typed names remains a
  follow-up before a wide launch.
- **Account deletion**: Google Play requires apps that let users create an account to let them
  request its deletion, in the app and on the web. `SocialStore.forgetPlayer(id)` removes
  friendships on both sides, requests, challenges, club membership (handing the club over),
  club contributions and leaderboard and tournament entries, sweeping every stored file so
  nothing that names the player is left. Full account deletion (`AccountStore.delete`, with its
  in-app and web entry points) calls it as a deletion listener; `POST /api/social/forget` (the
  "Delete my social data" button) calls it for social data alone.
- **Data kept**: week files are deleted once their rewards can't be claimed any more (after about
  5 weeks); old club weeks are dropped on save.

## Sources

- Niantic, Adding friends: https://niantic.helpshift.com/hc/faq/2938-adding-friends
- Prima Games, Pokémon GO friends: https://www.primagames.com/games/pokemon-go/tips/pokemon-go-how-add-friends-and-increase-friendship-level
- Bulbapedia, Friends (GO): https://bulbapedia.bulbagarden.net/wiki/Friends_(GO)
- Bulbapedia, Friend code: https://bulbapedia.bulbagarden.net/wiki/Friend_code
- Nintendo Support, friend codes: https://en-americas-support.nintendo.com/app/answers/detail/a_id/22438/~/how-to-view-or-change-your-friend-code
- Nintendo Support, Switch friends: https://nintendo.com/ph/support/switch/friends/index.html
- Duolingo leagues (Deconstructor of Fun): https://duolingo.deconstructoroffun.com/mechanics/leagues
- Duolingo leagues explained (The Mary Sue): https://themarysue.com/all-duolingo-leagues-explained
- Google Play Games Services, Leaderboards: https://developer.android.com/games/pgs/leaderboards
- Open Hexagon leaderboard anti-cheat (Gigazine): https://gigazine.net/gsc_news/en/20211110-open-hexagon-against-cheet
- Open Hexagon leaderboards devlog (GameDev.net): https://gamedev.net/blogs/entry/2271580-open-hexagon-leaderboards-devlog-0
- Server-authoritative validation for a prize game (DEV): https://dev.to/gowtham_r_2002/dont-trust-the-score-building-server-authoritative-validation-for-a-prize-based-mobile-game-246i
- Leaderboard cheating and fake scores (Bugnet): https://bugnet.io/blog/how-to-fix-leaderboard-cheating-and-fake-scores
- Tournament glossary (MWM): https://mwm.ai/glossary/tournament
- SimCity BuildIt, Contest of Mayors (ironSource LevelUp): https://medium.com/ironsource-levelup/simcity-buildit-contest-of-mayors-434441f0b555
- Royal Match, King's Cup guide: https://oldcynic.com/royal-match-kings-cup-guide-tips
- Royal Match, Royal League (Dream Games): https://dreamgames.helpshift.com/hc/en/3-royal-match/faq/108-royal-league
- Fishing Clash, clan match rewards (Ten Square Games): https://tensquaregames.helpshift.com/hc/en/3-fishing-clash/faq/4-why-have-i-not-received-clan-match-rewards
- Clash Royale, Clan Roles (Supercell): https://support.clashroyale.com/hc/en-us/articles/49484954095771-Clan-Roles
- Clash Royale, Automated Leader Rotation (Supercell): https://support.clashroyale.com/hc/en-us/articles/49484911201563-Automated-Leader-Rotation
- Clash Royale Wiki, Clans: https://clashroyale.fandom.com/wiki/Clans
- Brawl Stars clubs and roles (MobileMatters): https://mobilematters.gg/brawl-stars/brawl-stars-guide-to-clubs-roles
- Clash of Clans, Clan Games (Supercell): https://supercell.com/en/games/clashofclans/blog/news/upcoming-maintenance-break-clan-games-info
- Clash of Clans, Clan Games rewards (TheGamer): https://www.thegamer.com/clash-clans-master-clan-games-maximize-rewards/
- Preset usernames for children (library FAQ, COPPA): https://wccls.libanswers.com/bibliocommons/faq/236908
- Xbox XR-018, user-generated content: https://devdocs.xbox.com/publishing/certification/xr/xr-018
- ADL on hateful usernames in games: https://giga.law/daily-news/2023/7/25/popular-online-games-fail-to-restrict-hateful-usernames-adl-says
- Lichess username policy: https://www.github.com/lichess-org/lila/wiki/Username-policy
- Google Play, app account deletion requirements: https://support.google.com/googleplay/android-developer/answer/13327111
