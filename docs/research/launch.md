# Launching on Google Play: research and decisions

Research behind Deep Line's launch kit: the in-app review prompt, the release build (signing,
App Bundle, R8, cleartext), what a new personal Play Console account must do before production,
the store listing and its translations, the content rating and audience questions, and the
server's hosting. Written for a developer in France publishing from a new personal account in
November 2026. This is engineering research, not legal advice.

**How the sources were read.** developer.android.com was fetched directly. support.google.com
and play.google.com can't be reached from the build machine, so Play Console Help pages were read
through web search results quoting them (marked *(re-check)* where only secondary sources or
search snippets confirmed a number). Library versions were checked on Google's Maven repository
(dl.google.com). Re-read the pages marked *(re-check)* in Play Console before relying on them:
Play's numbers change often.

## Decisions at a glance

| Topic | Decision | Section |
| --- | --- | --- |
| Review prompt | Play In-App Review API (`review-ktx` 2.0.2) behind `ReviewPrompter`; asked only right after a rare catch, a new species or a battle won by playing, after 25 solo casts over 3 different days, never within an hour of a setback, at most once every 120 days and 3 times in all; never during a cast, a tour, a dialog or a payment; no question first, no button, no reward. | §1 |
| Testing before production | Closed test with at least **12 testers opted in for 14 days in a row**, then apply for production access. Plan 15 to 20 testers so drop-outs don't reset the count. | §2 |
| Target API | targetSdk **36** (required for new apps and updates since 31 Aug 2026). Already set. | §3 |
| 16 KB pages | The app has no native code of its own; the two AndroidX `.so` files in the bundle are 16 KB aligned (checked with `readelf`). AGP 8.13 aligns uncompressed libraries. `tools/release/check-release.sh` re-checks every build. | §3 |
| Signing | Play App Signing (Google keeps the app signing key); the developer keeps an **upload key** made with `keytool`, read by Gradle from `~/.gradle/gradle.properties` or environment variables, never committed. Without it, `bundleRelease` still builds an unsigned bundle (for CI and checks). | §4 |
| Format | Android App Bundle (`./gradlew :app:bundleRelease`), required for new apps since Aug 2021. | §4 |
| R8 | On for release (`isMinifyEnabled`, `isShrinkResources`), with the libraries' own consumer rules plus one `-dontwarn` in `app/proguard-rules.pro`. Built and inspected here (APK 4.7 MB instead of 19.6 MB for debug, bundle 9.0 MB instead of 13.7 MB); it must still be smoke-tested on a phone through the internal test track (§5). | §5 |
| Versioning | `app/version.properties`: `versionCode` +1 for every upload, `versionName` semantic (1.0.0 at launch); `-PdeeplineVersionCode` overrides it on CI. | §6 |
| Cleartext | Off in release (network security config), allowed in debug only (local server on `ws://10.0.2.2`). Release builds must use `-PdeeplineServerUrl=wss://...`. | §11 |
| Content rating | IARC questionnaire answers in `docs/store/content-rating.md`: no violence beyond fishing, no gambling (wagers use points that can't be cashed out; flagged), in-app purchases yes, users interact (names typed for battles, friends), no chat. Expected PEGI 3 / ESRB Everyone, with "In-Game Purchases" and "Users Interact" notices. | §8 |
| Audience | Kept at **13+ (not designed for children)**, with the Families question flagged again for the developer (France's age of digital consent is 15; cute art may draw Play's attention). | §9 |
| Store listing | Brand plus the main search term in the title ("Deep Line: Deep Sea Fishing", ≤ 30 characters), keywords in the short description, the core loop in the first lines, six languages written natively (en, fr, es, de, pt-BR, tr), checked by `StoreListingTest`. | §7, §10 |
| Graphics | Feature graphic 1024×500 rendered from the game's own 3D renderer; adaptive launcher icon with a monochrome layer; 512×512 Play icon rendered from the same vector layers. | §7 |
| Promo video | 1920×1080 (YouTube shows promo videos in landscape), 24 s, real 3D frames of casts rendered by the WebGL preview, the game's CC0 music. | §7 |
| Rollout | 10% → 20% → 50% → 100% over about a week, halting on crash rate > 1.09% or ANR rate > 0.47% (Play's bad-behaviour thresholds). | §12 |

## 1. The Play In-App Review API

What Google says (developer.android.com, *In-app reviews*):

- **When**: "Trigger the in-app review flow after a user has experienced enough of your app or
  game to provide useful feedback." "Don't prompt the user excessively for a review." "Your app
  shouldn't ask the user any questions before or while presenting the rating button or card,
  including questions about their opinion (such as 'Do you like the app?') or predictive
  questions (such as 'Would you rate this app 5 stars')."
- **Design**: show the card as-is, no overlay on or around it, don't remove it programmatically.
- **Quota**: "Google Play enforces a time-bound quota on how often a user can be shown the review
  dialog. Because of this quota, calling the `launchReviewFlow` method more than once during a
  short period of time (for example, less than a month) might not always display a dialog." The
  exact quota is an implementation detail that can change without notice. Hence: "you should not
  have a call-to-action option (such as a button) to trigger the API", because the card may not
  appear.
- **No result**: "The API does not indicate whether the user reviewed or not, or even whether the
  review dialog was shown." On an error, "do not inform the user or change your app's normal user
  flow."
- **ReviewInfo** is short-lived: request it only once the app is certain to launch the flow.
- **Library**: `com.google.android.play:review:2.0.2` and `review-ktx:2.0.2` (the latest on
  Google Maven, Oct 2024). Works on Android 5.0+ with the Play Store installed.
- **Data safety**: the rating and review text are handled by the Play Store under Play's terms,
  not by the app; nothing to add to our Data safety answers.
- **Incentives**: Play's policy forbids offering money, goods "or the equivalent" (in-game
  currency) for a rating or review (Android Developers Blog, 2017; Play's *User Ratings, Reviews,
  and Installs* policy *(re-check)*). Gating ("happy? rate us; unhappy? send feedback") is also
  against Google's guidance (no question before the card).
- **Common practice for games** (ASO and vendor guides, not Google): ask after a win, a level
  completed or an achievement; never mid-game, after a loss, during the tutorial, or after a crash
  or error.

**Decisions** (`core/.../ReviewPrompts.kt`, numbers in `GameConfig.Review`, app side in
`app/.../review/`):

- Happy moments: a solo cast that landed a rare species (3+ rarity stars), a Sparkling fish or the
  Deep Sovereign (`RARE_CATCH`), a species never caught before (`NEW_SPECIES`), a battle against
  the computer or online won by playing (`BATTLE_WON`; a win by the opponent's forfeit doesn't
  count, nor pass and play).
- Enough play: 25 solo casts (about a week at three casts a day, the economy model's pace) over 3
  different local days.
- Setbacks block the prompt for 60 minutes: a lost battle (forfeits included), a failed,
  cancelled or unavailable purchase, a quit cast, a dropped online connection.
- At most once every 120 days and 3 times per phone. Play's own quota (about a month) applies on
  top and silently drops extra calls.
- The card only opens on a calm screen: the result screen has been collected, no cast, tour,
  secret explanation, test-store dialog, gift sheet, consent sheet, reminder explainer or
  tournament attempt is under way. Starting a cast drops the pending moment.
- No "Rate us" button anywhere, no question first, nothing given for a review.
- The ask is recorded (`review_prompt` analytics event, device preferences) before Play answers,
  since the game can't know what Play did. The state is device-only (`ReviewPrefs`), like the
  consent and reminder choices.
- Robolectric gets `NoReviewPrompter`; tests install a recording fake through
  `ReviewPrompters.factory`. To try the real card, use an internal testing track or internal app
  sharing (Google's *Test in-app reviews* page): the card doesn't show for side-loaded debug
  builds.

## 2. New personal developer accounts: testing before production

- Personal accounts created after **13 November 2023** must "run a closed test for their app with
  a minimum of **12 testers** who have been opted in continuously for at least **14 days**"
  before they can apply for production access (Play Console Help, *App testing requirements for
  new personal developer accounts*, quoted by search results; several 2026 guides agree). The
  number was 20 until late 2024 (secondary sources). Until then, **Production** and
  **Pre-registration** are disabled in Play Console. *(re-check the numbers in Play Console's
  dashboard, which shows a live counter)*
- Organisation accounts (D-U-N-S number) are exempt; the developer here is a person, so it
  applies.
- After the 14 days, Play Console asks a short questionnaire (how testers were recruited, what
  feedback came back, what changed, why the app is ready). Answer it from the closed test's
  notes: keep a log of feedback and fixes during the test.
- Practical points from 2026 guides (secondary, *re-check*): the 14 days must be continuous;
  testers must opt in (accept the invitation link) and keep the app installed; Google reportedly
  looks at real engagement, so testers should actually play. Invite 15 to 20 people (friends,
  family, a Discord or Reddit community) to keep at least 12 throughout.
- New personal accounts also verify their identity and contact details and, per Google's account
  creation pages, may have to prove access to an Android device with the Play Console app
  *(re-check)*.
- Every app from such an account needs its own closed test; updates to a published app don't.

## 3. Target API level and 16 KB page sizes

- **Target API** (developer.android.com, *Meet Google Play's target API level requirement*):
  "Starting August 31 2026: New apps and app updates must target Android 16 (API level 36) or
  higher to be submitted to Google Play." Deep Line targets 36 and compiles against 36. Existing
  apps must target 35+ to stay visible to new users on newer Android versions.
- **16 KB page sizes** (developer.android.com, *Support 16 KB page sizes*): "all apps targeting
  Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on
  Google Play. Starting February 1, 2027, if your app updates don't support 16 KB memory page
  sizes, you won't be able to release these updates." Apps written only in Kotlin/Java "already
  support 16 KB devices". Deep Line has no C/C++ of its own, but the bundle carries two small
  AndroidX libraries (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`). Checked
  on the release bundle: every `LOAD` segment of the arm64-v8a and x86_64 copies is aligned to
  `0x4000` (16 KB), and their `GNU_RELRO` segments end their `LOAD` segment (compliant per the
  page's rule 3). AGP 8.13 (≥ 8.5.1) zip-aligns uncompressed libraries to 16 KB.
  `tools/release/check-release.sh` repeats both checks (`readelf`, `zipalign -c -P 16`) on every
  build.

## 4. Signing and the App Bundle

- **App Bundle**: "From August 2021, new apps are required to publish with the Android App Bundle
  on Google Play." The compressed download (base + configuration APKs) may be up to 4 GB; the
  game's bundle is about 14 MB.
- **Play App Signing** is required for new apps (since August 2021). Google keeps the **app
  signing key**; the developer signs uploads with an **upload key**. "If you lose your upload key,
  or if it is compromised, you can request an upload key reset in the Play Console." Let Google
  generate the app signing key (the default) unless the same key is needed in another store.
- **Upload key**: a keystore made with `keytool` (or Android Studio); the key "should be valid for
  at least 25 years" (Play requires validity past 22 October 2033). Keep the keystore and
  passwords out of the repository: Google's page suggests a separate properties file; here Gradle
  reads `deeplineUpload*` properties (`~/.gradle/gradle.properties`) or `DEEPLINE_UPLOAD_*`
  environment variables (CI secrets). Commands: `docs/release-checklist.md` and `README.md`.
- The upload certificate (`keytool -export -rfc ...`) is what to give Google for an upload key
  reset; back up the keystore in two places (e.g. a password manager and an offline drive).

## 5. R8 (shrinking, optimisation, obfuscation)

- Google: "You should always enable optimization for your app's release build" (`isMinifyEnabled
  = true` and `isShrinkResources = true` with `proguard-android-optimize.txt` on AGP < 9.3; the
  new `optimization { enable = true }` DSL is AGP 9.3+). Full mode is the default since AGP 8.0.
  Android vitals now also flags poorly optimised DEX code (threshold for games: > 50 MB of DEX;
  store visibility effects from February 2027), so R8 is the expected setup.
- **Library rules**: kotlinx.serialization ships its own R8 rules (the project serialises with
  explicit `serializer()` calls, the safest pattern); Play Billing, Firebase, Play Review,
  WorkManager, OkHttp and coroutines ship consumer rules. Ktor has no official rules for its
  client; it is created here with an explicit engine (`HttpClient(OkHttp)`), so no
  `ServiceLoader` lookup of engines is needed. R8 reported a single missing class, an annotation
  `review-ktx` 2.0.2 references from a newer play-services-basement
  (`com.google.android.gms.common.annotation.NoNullnessRewrite`), silenced with `-dontwarn` in
  `app/proguard-rules.pro`.
- **Crash reports**: the App Bundle carries the R8 mapping file, which Play Console uses to
  deobfuscate Android vitals; the Crashlytics Gradle plugin uploads it too once
  `google-services.json` exists.
- **Verification here**: no emulator or phone on the build machine. The minified bundle was built
  and inspected: `MainActivity` and `ReminderWorker` keep their names (manifest and WorkManager
  look them up by name), `GameViewModel`'s `(Application)` constructor is kept (lifecycle's rule
  for the reflective factory), the kotlinx.serialization and WorkManager rules are in R8's
  configuration, and the bundle carries the mapping file. A signed, strict build with a test key
  passed `tools/release/check-release.sh`. **Before the closed test, install
  the internal-track build on a real phone and walk the main paths** (first launch and consent,
  tour, a cast, a battle against the computer, an online battle, the Shop with a test purchase,
  Settings > Privacy, a reminder), and read the pre-launch report. To turn R8 off in an
  emergency: `-PdeeplineMinify=false`.

## 6. Versioning

- `versionCode`: a positive integer, greater for every upload to any track, never reused; Play's
  maximum is **2100000000**. `versionName`: free text shown to players.
- Decision: `app/version.properties` holds both (1 and 1.0.0 for the first upload). Bump
  `versionCode` by one for every bundle sent to Play (internal builds included); bump
  `versionName` by semantic versioning (1.0.1 fixes, 1.1.0 features). CI can pass
  `-PdeeplineVersionCode=<n>` instead of editing the file.

## 7. Store listing rules and graphics

- **Text limits**: app name **30** characters (Play Console Help, *Best practice for your store
  listing*: "your app title must be 30 characters or less"; the cut from 50 to 30 dates from
  2021), short description **80**, full description **4000**.
- **Metadata policy**: no misleading or excessive metadata, no keyword stuffing, no claims of
  performance or ranking ("best", "#1", "top", "popular", "app of the year"), no price or
  promotion in the title, icon or developer name, no emoji or repeated special characters in the
  title, no testimonials from unattributed users. The same rules apply to every translation.
  The short and full descriptions shouldn't repeat each other.
- **App icon** (developer.android.com, *Google Play icon design specifications*): 512×512, 32-bit
  PNG, sRGB, at most 1024 KB, full square (Play applies a 30% corner radius and the shadow); no
  text or badges suggesting ranking, deals or Play programmes.
- **Feature graphic**: 1024×500, JPEG or 24-bit PNG **without alpha**; required to publish and to
  show a promo video. Keep the important part away from the edges (Play may crop or overlay it)
  and add alt text.
- **Screenshots** (Play Console Help, *Add preview assets*, via secondary sources *(re-check)*):
  2 to 8 per device type, JPEG or 24-bit PNG without alpha, each side 320 to 3840 px, the long
  side at most twice the short one. For a portrait game, 1080×1920 (9:16) works everywhere; Play
  recommends at least 4 screenshots of at least 1080 px for recommendations. Screenshots with text
  need a version per language; alt text for each.
- **Promo video**: a YouTube URL (not a playlist or channel), public or unlisted, ads off (no
  monetisation), not age-restricted, shown in landscape; a feature graphic must exist. Guides
  suggest 30 seconds to 2 minutes, others "under 30 seconds" *(re-check)*. Deep Line's is 24 s,
  1920×1080, with the portrait gameplay framed by captions.
- **Localisation**: Play machine-translates listings that aren't translated, but hand-written
  translations read better and rank better for local searches (ASO guides). Custom store listings
  can target countries later.

## 8. Content rating (IARC)

- Every app needs a content rating from the IARC questionnaire in Play Console (*App content >
  Content rating*); the answers produce PEGI (Europe), USK (Germany), ESRB (Americas), ClassInd
  (Brazil) and other ratings. A wrong answer can get the rating overridden or the app removed.
  Re-do it when content changes (chat, gambling-like features).
- Topics: violence, fear, sexuality, language, controlled substances, crude humour, gambling
  (real and simulated), user interaction and sharing (chat, user-generated content, location),
  digital purchases, and whether the app is a game.
- Deep Line: fishing only (fish are caught, never shown hurt; no blood), a "Nightmare" look for the
  deep that the player unlocks and chooses (spooky fish, no gore), no language, no gambling with
  real money or with anything cashable. **Wagers** stake game points against the computer: a
  wager isn't chance-based (it's a skill battle) and points can't be bought back out, but the
  questionnaire's "simulated gambling" question should be read carefully; answers and reasoning:
  `docs/store/content-rating.md`. Users interact (typed battle names, friends) but there is no
  chat or free messaging. Digital purchases: yes.

## 9. Target audience and the Families policy

- *Target audience and content* in Play Console: the declared age groups decide whether the
  Families policies apply. "Any apps that have at least one target audience age group that
  includes children must comply with Google Play's Families policy requirements." Google also
  reviews the store listing and may decide an app "unintentionally appeals to children", then ask
  to change the marketing, the audience, or add a disclaimer.
- Deep Line's assumption (analytics research §8) stays: **13+, not designed for children**, no
  ads. **Still to confirm by the developer**, now with the store listing in mind:
  - The art is cute and cartoonish (round fish, bright colours): keep the listing's words and
    pictures aimed at teens and adults (collection, depth, battles, rankings), avoid "for kids",
    and answer "No" to "Could your store listing unintentionally appeal to children?" only if
    that's honestly true.
  - France's age of digital consent is 15: options are 16+ (simplest legally), or an age question
    before the consent sheet, or accepting the residual risk (pseudonymous, ad-free statistics,
    off by default). See `docs/store/target-audience.md`.

## 10. ASO for a casual collection game

From Google's store listing best practices and ASO guides (AppTweak, Asodesk, appradar,
Gummicube, 2025-2026; vendor opinion where noted):

- Play has no keyword field: it indexes the **title** (most weight), the **short description**
  and the **full description** (vendor guides). Put the main search terms in the title and the
  short description naturally: "fishing game", "fish collection", "deep sea"; repeat the important
  ones a few times in the full description, never as a list of keywords.
- The **first lines** of the full description are what most people read before "More": state the
  core loop (cast, steer, dodge, hook fish on the way up, fill the Dex) in the first 2-3 lines.
- **Screenshots** decide conversion more than text: the first two or three must show the core loop
  and the game's look; one short caption per screenshot (vendors report annotated screenshots
  outperform plain ones); same order in every language.
- Localise the title, descriptions and screenshot captions for the biggest markets you target;
  re-use the title's brand ("Deep Line") everywhere and translate the descriptive part.
- Store listing experiments (Play Console) later: one change at a time (icon, first screenshot),
  at least 7 days each.
- Ratings and retention feed rankings: the review prompt (§1) aims at engaged, happy players.

## 11. Server hosting and TLS (what the Data safety answers imply)

- The Data safety form asks whether all user data is encrypted in transit; `docs/play-data-safety.md`
  answers **yes only if** the production server is behind HTTPS/WSS. Decisions:
  - Release builds use a **network security config with cleartext off** (the platform default for
    targetSdk 28+, made explicit), and the old `android:usesCleartextTraffic="true"` is gone.
    Debug builds keep cleartext for the local development server (`src/debug/res/xml`).
  - Release builds must be built with `-PdeeplineServerUrl=wss://<host>/battle`; the build warns
    when a release task runs with a `ws://` address, and `-PdeeplineStrictRelease=true` turns every
    release warning (unsigned, cleartext URL, placeholder privacy policy) into an error.
  - A server address typed in the app's Server settings with `ws://` simply fails in release (the
    platform refuses cleartext), which keeps the "encrypted in transit" answer true.
- Hosting: any Docker host with TLS termination (Fly.io, Render, Railway, a VPS behind Caddy or
  nginx with Let's Encrypt). WebSockets must pass through the proxy (`/battle`), plus the HTTP API
  and `/delete-account`. Keep `data/` on a persistent volume, set the environment variables from
  the README (`DEEPLINE_DATA_DIR`, `DEEPLINE_SOCIAL_DIR`, `DEEPLINE_TOURNAMENT_SALT`,
  `DEEPLINE_PRIVACY_CONTACT`, `DEEPLINE_PRIVACY_POLICY_URL`, `DEEPLINE_CLIENT_IP_HEADER` behind a
  proxy, `DEEPLINE_CLUBS=off` until clubs ship). Choose an EU region (the privacy policy names the
  host and country).
- Google Play's account deletion requirement needs the web link (`https://<host>/delete-account`)
  to work over HTTPS when the listing is submitted.

## 12. Pre-launch report, rollout and monitoring

- **Pre-launch report**: when a build reaches a testing track, Google runs it on real devices
  with a Robo crawler (Firebase Test Lab) and reports stability (crashes, ANRs), performance,
  accessibility (content labels, contrast, touch targets) and security issues (for example
  "allowing cleartext traffic"). Crashes found there don't count in Android vitals. It isn't a
  formal gate, but fix what it finds before production. The crawler stops at the consent sheet
  and tour unless it taps through: it can, as both are plain buttons.
- **Staged rollout**: Play doesn't raise the percentage automatically; a release can be halted
  (users who have it keep it) and resumed; there's no rollback, only a fixed new build. Decision:
  10% for 1-2 days, 20%, 50%, then 100% over about a week, checking vitals and reviews at each
  step (common practice; guides often start at 1-5% for bigger apps).
- **Android vitals** bad-behaviour thresholds (developer.android.com, *Android vitals*), over 28
  days: user-perceived crash rate **1.09%** overall and **8%** per phone model; user-perceived ANR
  rate **0.47%** overall and **8%** per phone model; excessive partial wake locks 5%. Above them,
  "Play may reduce the visibility of your title" and show a warning on the store listing. Memory
  thresholds for games (e.g. 2.25 GB foreground on 4 GB phones) may affect visibility from
  February 2027.
- Retention benchmarks for casual games (vendor reports, not Google): day-1 around 30-40%,
  day-7 around 10-15%, day-30 around 3-6%. Track them in Play Console (*User acquisition* and
  *Retention*) and Firebase.

## 13. Points left open (for the developer)

- **Families/age question** (§9) and the **wager** answer in the rating questionnaire (§8).
- **Trader status (EU Digital Services Act)**: Apple shows traders' address, phone and email to EU
  users; Play Console also asks developers whether they are a trader. Selling in-app purchases
  likely makes the developer a trader, and a personal account's details may then be public.
  Re-check Play Console's *Developer account > About you* page, and consider a business address or
  a micro-enterprise.
- **Payments profile**: selling in-app products needs a Google payments merchant profile (bank
  account, tax information; VAT is collected and paid by Google for EU sales).
- **Testers**: recruit 15-20 for the closed test (§2) and keep a feedback log for the production
  access questionnaire.
- **Device check of the R8 build** (§5).

## Sources

Android Developers (fetched directly)
- [In-app reviews (Google Play)](https://developer.android.com/guide/playcore/in-app-review), [Integrate in-app reviews (Kotlin or Java)](https://developer.android.com/guide/playcore/in-app-review/kotlin-java), [Test in-app reviews](https://developer.android.com/guide/playcore/in-app-review/test)
- [Meet Google Play's target API level requirement](https://developer.android.com/google/play/requirements/target-sdk)
- [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)
- [Sign your app (Play App Signing, upload key)](https://developer.android.com/studio/publish/app-signing)
- [About Android App Bundles](https://developer.android.com/guide/app-bundle)
- [Enable app optimization (R8)](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
- [Version your app](https://developer.android.com/studio/publish/versioning)
- [Network security configuration](https://developer.android.com/privacy-and-security/security-config)
- [Android vitals](https://developer.android.com/topic/performance/vitals)
- [Google Play icon design specifications](https://developer.android.com/google-play/resources/icon-design-specifications)
- [Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)
- Library versions: [review-ktx on Google Maven](https://dl.google.com/dl/android/maven2/com/google/android/play/review-ktx/maven-metadata.xml)

Play Console Help (read through search results; *re-check*)
- [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Best practice for your store listing](https://support.google.com/googleplay/android-developer/answer/13393723)
- [Add preview assets to showcase your app](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Metadata policy](https://support.google.com/googleplay/android-developer/answer/9898842)
- [Content ratings](https://support.google.com/googleplay/android-developer/answer/9859655) and [Apps & Games content ratings on Google Play](https://support.google.com/googleplay/answer/6209544)
- [Manage target audience and app content settings](https://support.google.com/googleplay/android-developer/answer/9867159) and [Families policies](https://support.google.com/googleplay/android-developer/answer/9893335)
- [Use the pre-launch report](https://support.google.com/googleplay/android-developer/answer/9844487)
- [Release app updates with staged roll-outs](https://support.google.com/googleplay/android-developer/answer/6346149)
- [Google Play's policy on incentivized ratings, reviews and installs (Android Developers Blog, 2017)](https://android-developers.googleblog.com/2017/06/google-plays-policy-on-incentivized.html)

Secondary sources (ASO, practice)
- [Google Play 12 testers policy guides (2026)](https://www.testerscommunity.com/blog/google-play-closed-testing-requirements-2026), [Choicely: the 12 tester rule](https://www.choicely.com/blog/google-play-12-tester-rule)
- [AppTweak: ASO checklist for Google Play](https://www.apptweak.com/en/aso-blog/app-store-optimization-aso-checklist-for-google-play), [Asodesk: ASO checklist](https://asodesk.com/blog/checklist-for-aso-in-google-play/), [Gummicube: how ASO works on Google Play](https://gummicube.com/how-aso-works-google)
- [appradar: Google Play promo video](https://appradar.com/academy/google-play-app-store-optimization/promo-video), [apptamin: promo video guide](https://www.apptamin.com/promo-video/guide-promo-videos-play-store/)
- [Adapty: when to ask for app reviews](https://adapty.io/blog/how-to-get-more-app-reviews/)
- [aso.dev: Google Play rollouts](https://www.aso.dev/google-play/rollout)
- [kotlinx.serialization R8 rules](https://github.com/Kotlin/kotlinx.serialization/issues/1121)
- [MacRumors: EU trader requirement (App Store)](https://www.macrumors.com/2024/10/17/developers-eu-app-store-trader-requirements/)
