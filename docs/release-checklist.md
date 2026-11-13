# Release checklist: from now to production

Every step to publish Deep Line on Google Play from a new **personal** developer account, in
order. Numbers and sources: [`docs/research/launch.md`](research/launch.md). Store texts and
answers: [`docs/store/`](store/README.md). Tick the boxes as you go (this file is meant to be
edited).

Expect about **four weeks** end to end: a week of setup, at least **14 days** of closed testing
(required), a few days for production access review, then a staged rollout over a week.

## 1. Accounts and legal (week 1)

- [ ] Create the Google Play developer account (personal), pay the one-time fee, verify your
      identity, email and phone; install the Play Console app on an Android phone if asked to
      verify device access.
- [ ] Decide what's public: Play shows the developer name, and, for apps with in-app purchases,
      likely contact details under the EU Digital Services Act "trader" rules. Consider a
      dedicated email address (and a business address or micro-enterprise) before filling in
      *Account details*. *(re-check: launch.md §13)*
- [ ] Set up the **payments profile** (merchant account, bank, tax forms) so in-app products can
      be sold.
- [ ] Decide the audience (**the Families question**, [`store/target-audience.md`](store/target-audience.md)):
      13+ or 16+. Decide the **wager** answer for the content rating
      ([`store/content-rating.md`](store/content-rating.md)).

## 2. Firebase (optional, analytics and crash reports)

- [ ] Follow [`docs/firebase-setup.md`](firebase-setup.md): create the project, add
      `app/google-services.json`, accept the data processing terms, set the shortest data
      retention, enable Crashlytics.
- [ ] Without it the game collects nothing and asks nothing; the Data safety answers shrink
      accordingly ([`store/data-safety.md`](store/data-safety.md)).

## 3. Privacy policy and the game server (week 1)

- [ ] Fill in [`docs/privacy-policy.md`](privacy-policy.md) (name, address, contact, host,
      retention), publish it in English and French on a public HTTPS page (GitHub Pages, your
      site, or the server).
- [ ] Host the battle server with **TLS** (see the README's *Battle server* section): any Docker
      host behind HTTPS (Fly.io, Render, Railway, or a VPS with Caddy/nginx and Let's Encrypt),
      in an EU region, with WebSockets passed through. Players connect to `wss://<host>/battle`.
- [ ] Persistent volume for `data/`; environment variables:
  - [ ] `DEEPLINE_DATA_DIR` and `DEEPLINE_SOCIAL_DIR` on the volume;
  - [ ] `DEEPLINE_TOURNAMENT_SALT` (a secret number, set once, never changed mid-week);
  - [ ] `DEEPLINE_PRIVACY_CONTACT` (email) and `DEEPLINE_PRIVACY_POLICY_URL`;
  - [ ] `DEEPLINE_CLIENT_IP_HEADER` if the host's proxy sets one (e.g. `Fly-Client-IP`);
  - [ ] `DEEPLINE_CLUBS=off` (clubs ship later; release builds don't call them anyway).
- [ ] Check `https://<host>/delete-account` opens and deletes an account (it is the **account
      deletion URL** Play asks for).
- [ ] Set up a backup of the volume and an uptime check (the review and testers need the server).
- [ ] **Per-address rate limit at the proxy** (Cloudflare rate-limiting rules, nginx
      `limit_req`, Caddy's rate-limit module or the host's equivalent) on `/api/` and
      `/delete-account`, e.g. 60 requests a minute per IP. The server limits social calls per
      address itself, but any first call with a new player ID creates an account file, so without
      a limit in front a script could fill the disk with accounts.

**Known server limits** (fine for a launch, revisit as the game grows; from the final review):
- New accounts are created on first use and are unbounded per address (hence the proxy limit
  above); an ID is claimed by the first secret used with it.
- `SocialStore.forgetPlayer` sweeps every social file under the social lock, so one account
  deletion pauses social calls longer as players grow, and week files are rewritten whole on each
  write: move social data to a database (or index players' files) before tens of thousands of
  players.
- The rate limiter keeps a key per address in memory and prunes it past 100,000 keys.

## 4. The upload key (once, then keep it safe)

Play App Signing keeps the real app signing key; you sign uploads with an **upload key**. If you
lose it, Play can reset it, but keep two backups anyway.

```bash
keytool -genkeypair -v -keystore ~/keys/deepline-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=<your name>, O=Deep Line, C=FR"
# export the certificate (for an upload key reset or API providers)
keytool -export -rfc -keystore ~/keys/deepline-upload.jks -alias upload -file deepline-upload.pem
```

- [ ] Store the keystore **outside the repository** (it's in `.gitignore` anyway) and put the
      passwords in a password manager; back up both.
- [ ] Tell Gradle where it is, in `~/.gradle/gradle.properties` (never the project's file):

```properties
deeplineUploadStoreFile=/home/<you>/keys/deepline-upload.jks
deeplineUploadStorePassword=...
deeplineUploadKeyAlias=upload
deeplineUploadKeyPassword=...
```

  or, on CI, the environment variables `DEEPLINE_UPLOAD_STORE_FILE`,
  `DEEPLINE_UPLOAD_STORE_PASSWORD`, `DEEPLINE_UPLOAD_KEY_ALIAS`, `DEEPLINE_UPLOAD_KEY_PASSWORD`.
  Without them `bundleRelease` still builds, unsigned (Play refuses unsigned bundles).

## 5. Build the release bundle

- [ ] Bump `versionCode` in [`app/version.properties`](../app/version.properties) for **every**
      upload (any track); set `versionName` (1.0.0 for the first release).
- [ ] Build with the production addresses and strict checks:

```bash
./gradlew :core:test :server:test :app:testDebugUnitTest
./gradlew :app:bundleRelease :app:assembleRelease \
  -PdeeplineServerUrl=wss://<host>/battle \
  -PdeeplinePrivacyPolicyUrl=https://<site>/privacy \
  -PdeeplineStrictRelease=true
tools/release/check-release.sh
```

  `-PdeeplineStrictRelease=true` fails the build if it would be unsigned, point at a `ws://`
  server or the placeholder privacy policy. `check-release.sh` checks target SDK 36, not
  debuggable, cleartext refused, the addresses, 16 KB alignment, the signature and the R8
  mapping. The bundle is `app/build/outputs/bundle/release/app-release.aab`.
- [ ] R8 is on: if a release build misbehaves and you need a build now, `-PdeeplineMinify=false`
      turns it off (then fix the keep rules in `app/proguard-rules.pro`).

## 6. Play Console: create the app

- [ ] *Create app*: name "Deep Line", default language English (United States), **Game**,
      **Free**, accept the declarations.
- [ ] *Store settings*: category **Casual** (or Simulation), tags, contact email (and website).
- [ ] *Main store listing* in English from `docs/store/listings/en-US/`, then *Translations*:
      fr-FR, es-ES, de-DE, pt-BR, tr-TR (also es-419 if you want Latin America,
      [`store/keywords.md`](store/keywords.md)). Graphics: `docs/store/graphics/play-icon-512.png`,
      `docs/store/graphics/feature-graphic.png`, phone screenshots (done: 8 per language in
      `docs/store/listings/<locale>/images/phoneScreenshots/`, 1080 × 1920 JPEG, made from the game
      by `tools/store-screenshots/make-store-screenshots.sh`; add each one's alt text from
      [`store/screenshots.md`](store/screenshots.md), and remake them when the UI or a caption
      changes), promo video URL (upload the MP4 to YouTube,
      unlisted is fine, monetisation off; [`tools/promo-video/README.md`](../tools/promo-video/README.md)).
- [ ] *Policy > App content*:
  - [ ] Privacy policy URL (the published page);
  - [ ] Ads: **no ads**;
  - [ ] App access: everything available without special access;
  - [ ] Content rating questionnaire ([`store/content-rating.md`](store/content-rating.md));
  - [ ] Target audience and content ([`store/target-audience.md`](store/target-audience.md));
  - [ ] Data safety ([`docs/play-data-safety.md`](play-data-safety.md), checklist in
        [`store/data-safety.md`](store/data-safety.md)), with the deletion URL
        `https://<host>/delete-account`;
  - [ ] Advertising ID: **not used**;
  - [ ] Government, financial, health, news apps: no.
- [ ] *Monetize > Products*: create the in-app products with the shop's item ids (tackle packs
      and seasonal items **consumable**, the rest one-time), the `captains_club` subscription and
      the `gift_<id>` consumables (README, *Payments*); set prices per country from the euro prices.
- [ ] Link Firebase to Play (if Firebase is on: `docs/firebase-setup.md` §6).

## 7. Internal testing (a day or two)

- [ ] *Testing > Internal testing*: create a release, accept Play App Signing (let Google create
      the app signing key), upload the `.aab`, add yourself and a few testers by email.
- [ ] Install from the Play link on a real phone (not a side-loaded build) and walk the main
      paths: first launch, consent sheet, tour, a cast to the results, a battle against the
      computer, an online battle (two phones), the Shop with a real test purchase (license
      testers in *Setup > License testing* pay nothing), Settings > Privacy (delete account), a
      reminder the next day, and the review card (it only shows in builds installed from Play).
- [ ] This is the **R8 build's first run on a device**: watch for crashes in *Android vitals* /
      Crashlytics and for anything that works in debug but not here (serialization, billing,
      WorkManager reminders).
- [ ] Read the **pre-launch report** (*Testing > Pre-launch report*): crashes, ANRs,
      accessibility (labels, contrast, touch targets), security warnings. Fix what it finds.

## 8. Closed testing: at least 12 testers for 14 days (required)

- [ ] *Testing > Closed testing*: create a track, upload the same or a newer bundle, add testers
      (an email list or a Google Group) and share the opt-in link.
- [ ] Recruit **15 to 20 testers** so that at least **12 stay opted in for 14 days in a row**
      (Play's requirement for personal accounts created after 13 Nov 2023). Play Console's
      dashboard shows the live count; don't pause the track.
- [ ] Ask testers to really play (several sessions), and keep a log: who reported what, what you
      changed, which builds you shipped (new versionCode each time).
- [ ] After 14 days: *Apply for production access* and answer the questionnaire from the log
      (how you recruited testers, their feedback, what changed, why the app is ready). Review
      takes a few days.

## 9. Production

- [ ] Final bundle (new versionCode), `check-release.sh` all green, release notes from
      `docs/store/listings/<locale>/changelogs/<versionCode>.txt` in each language.
- [ ] *Production > Create release*, countries (all, or start with a few), **staged rollout
      10%**.
- [ ] Day 1-2 at 10%: check crash rate, ANR rate, reviews; then **20%**, then **50%**, then
      **100%** over about a week. **Halt** the rollout (users who have it keep it) if the
      user-perceived crash rate goes over **1.09%** or ANRs over **0.47%**, or on any data or
      payment problem; ship a fixed build with a new versionCode.

## 10. After launch: monitoring

- [ ] **Android vitals** (Play Console), daily during the rollout, weekly after: user-perceived
      crash rate under **1.09%** overall and **8%** per phone model, ANR rate under **0.47%**
      overall and **8%** per model (above them Play may lower visibility and warn on the listing);
      excessive wake locks under 5%; memory (games, 4 GB phones: 2.25 GB foreground) from Feb 2027.
      Turn on email alerts.
- [ ] Crashlytics (if on): new issues, with the R8 mapping uploaded automatically.
- [ ] **Retention** (Play Console *Statistics* / Firebase): day 1, day 7, day 30; typical casual
      games: about 30-40% / 10-15% / 3-6%. Compare with the onboarding funnel
      (`tutorial_begin` → `first_cast` → `first_catch`) and `level_end` depths
      ([`docs/analytics-events.md`](analytics-events.md)).
- [ ] Ratings and reviews: answer reviews (Play Console *Ratings and reviews*); the in-app
      review card asks happy, engaged players at most once every 120 days (`GameConfig.Review`).
- [ ] Economy and difficulty against real play: points per cast, rope progress, Lantern use
      (`EconomyTest`, `DifficultyTest` targets).
- [ ] Server: uptime, disk use of `data/`, the weekly tournament reset (Monday 00:00 UTC).
- [ ] Policy deadlines: target API (each August), 16 KB pages for updates from **1 Feb 2027**
      (already compliant), Play policy emails.

## Later updates

- Each update: new versionCode, release notes in 6 languages, tests, strict build, internal track
  first, staged rollout.
- **Clubs** (built, off in release): follow CLAUDE.md's "To ship clubs" steps, add them to the
  store listing, then update the Data safety answers if needed.
