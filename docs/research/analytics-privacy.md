# Analytics, crash reports, consent and privacy: research and decisions

Research behind Deep Line's analytics (Google Analytics for Firebase), crash reports (Firebase
Crashlytics), consent (GDPR, ePrivacy, CNIL, Google Consent Mode v2) and Google Play's privacy
requirements (Data safety form, account deletion, families). Written for a developer based in
France who publishes on Google Play. This is engineering research, not legal advice: have the
privacy policy and the Play declarations reviewed before launch.

**How the sources were read.** The build machine's network only reaches Google's Maven
repository, so the official pages below (firebase.google.com, developers.google.com,
support.google.com, cnil.fr, edpb.europa.eu, legifrance.gouv.fr) were consulted through web
search results quoting them, not opened directly. Library versions were checked on Google Maven
itself. Before publishing, re-read the pages marked *(re-check)*: they change often.

## Decisions at a glance

| Topic | Decision | Why (sections below) |
| --- | --- | --- |
| What we collect | Usage statistics (Google Analytics for Firebase) and crash reports (Crashlytics), as two separate opt-in choices. No ads, no advertising ID, no Android ID. | §1, §2, §5 |
| Before consent | Nothing at all: Firebase isn't even started (its auto-init provider is removed from the manifest), collection flags are off, Consent Mode v2 defaults are all denied. | §3, §4, §6 |
| Crash reports | Opt-in too (not treated as "strictly necessary"). | §6 |
| Consent sheet | Shown once on first launch, **before** the guided tour; the tour starts right after the answer. "Don't allow" and "Allow" are the same size and style, side by side; "Choose for each" sets the two choices separately, both off by default. It can't be dismissed without an answer. | §6 |
| Changing your mind | Two switches in Settings > Privacy, as easy to turn off as on. Withdrawing usage statistics also clears the analytics data on the device (new app instance id); withdrawing crash reports deletes unsent reports. | §2, §6 |
| How long the choice lasts | Until the player changes it. Asked again only if the question changes (`ConsentStore.CONSENT_VERSION`: a new purpose or recipient). | §6 |
| Consent Mode v2 | `analytics_storage` follows the usage statistics choice; `ad_storage`, `ad_user_data`, `ad_personalization` are always denied. | §3 |
| Firebase BoM | 34.19.0 (Analytics 23.2.0, Crashlytics 20.1.1). BoM 35 is compiled with Kotlin 2.4 metadata, which this project's Kotlin 2.2 can't read. | §1 |
| Builds without Firebase | Work unchanged: the google-services and Crashlytics Gradle plugins only apply when `app/google-services.json` exists; the app then installs no backend and asks nothing. | |
| Online account | In-app "Delete my online account" (Settings > Privacy) and a web page on the game server (`/delete-account`, takes the recovery code), both erasing the player's server file. | §7 |
| Audience | **Assumption: not a children's app.** Target audience 13+ in Play Console, no ads. **The developer must confirm** (see §8, including France's age of digital consent, 15). | §8 |

## 1. Google Analytics for Firebase in a game

- **Recommended game events.** Google's games list includes `level_start` (`level_name`),
  `level_end` (`level_name`, `success`), `post_score` (`score`, optional `level`, `character`),
  `unlock_achievement`, `tutorial_complete`, `level_up`, `select_content`;
  `earn_virtual_currency` (`virtual_currency_name`, `value`), `spend_virtual_currency`
  (`item_name`, `virtual_currency_name`, `value`) and `tutorial_begin` exist as SDK constants.
  Using recommended names gives Google Analytics' built-in reports without custom setup.
  *Decision*: casts are `level_start`/`level_end` (a cast is the game's "level"), the tour is
  `tutorial_begin`/`tutorial_complete`, points are `earn_`/`spend_virtual_currency`, milestones
  are `unlock_achievement`; everything else is a custom event. Taxonomy: `docs/analytics-events.md`.
- **Limits.** Event names up to 40 characters, alphanumeric and underscores, starting with a
  letter; up to 25 parameters per event; parameter names up to 40 characters; string values up
  to 100 characters (500 on Analytics 360); 500 distinct event names; user property names up to
  24 characters, values up to 36, at most 25 user properties. Prefixes `firebase_`, `google_`,
  `ga_` and some event names (`first_open`, `session_start`, `in_app_purchase`...) are reserved.
  *Decision*: `AnalyticsRules` encodes these and `AnalyticsTaxonomyTest` checks every name.
- **Screen views.** Automatic screen reporting logs activities; a single-activity Compose app
  would only ever report `MainActivity`. It can be turned off with the manifest flag
  `google_analytics_automatic_screen_reporting_enabled=false`, and screens logged manually as
  `screen_view` with `screen_name`/`screen_class`. *Decision*: done (`TrackScreens`).
- **User properties** describe segments (25 at most). *Decision*: six coarse ones (rope band,
  reach zone, Dex band, theme, night mode, Club member), never anything identifying.
- **In-app purchases.** Once the Firebase project is linked to Google Play, the SDK logs
  `in_app_purchase` automatically (product id, price, currency...), and manually logged purchase
  events are **not** de-duplicated against them. *Decision*: our `store_purchase` carries no
  price or currency, so revenue is never counted twice.
- **DebugView.** `adb shell setprop debug.firebase.analytics.app com.deepline.fishing` sends
  events with minimal delay (normally batched for about an hour) to Analytics > DebugView;
  `.none.` turns it off; verbose logs with `log.tag.FA`/`FA-SVC` set to `VERBOSE`. Steps in
  `docs/firebase-setup.md`.
- **Versions.** BoM 35.0.0 (Oct 2026) raised minSdk to 24 (ours is 24). Its measurement
  libraries carry Kotlin metadata 2.4.0, which the Kotlin 2.2.21 compiler used here rejects;
  BoM 34.19.0 (metadata 2.2.0) compiles. *Upgrade the BoM together with Kotlin (2.3+).*

## 2. Crashlytics with opt-in collection

- Automatic collection can be disabled with the manifest meta-data
  `firebase_crashlytics_collection_enabled=false`; `setCrashlyticsCollectionEnabled(true/false)`
  overrides the manifest and persists across launches.
- While collection is disabled, crash reports are still **stored on the device**;
  `sendUnsentReports()` uploads them, `deleteUnsentReports()` deletes them.
- Opt-in reporting lowers the accuracy of crash-free metrics (sessions of non-consenting users
  aren't counted).
- *Decision*: off by default in the manifest; switched on by the player's choice; when crash
  reports are refused, unsent reports are deleted. Because Firebase only starts after a first
  "yes" (§4), a player who never agrees never runs Crashlytics at all. Handled errors (3D
  renderer failures, store failures, unreachable server) are non-fatal reports
  (`recordException`) with the current screen as a custom key, at most once per kind per session.
  No user id is ever set on Crashlytics.

## 3. Google Consent Mode v2 (EEA)

- Consent types: `ANALYTICS_STORAGE`, `AD_STORAGE`, `AD_USER_DATA`, `AD_PERSONALIZATION`
  (v2 added the last two for the EU user consent policy), values `GRANTED`/`DENIED`. Needs
  Analytics SDK 21.5.0+.
- Defaults go in the manifest (`google_analytics_default_allow_analytics_storage`,
  `..._ad_storage`, `..._ad_user_data`, `..._ad_personalization_signals`, true or false); by
  default none is set, so an app that wants opt-in must set them to false. `setConsent(...)`
  overrides the defaults and persists until called again. Google's guide pairs this with
  temporarily disabling Analytics collection until consent.
- When `analytics_storage` (or `ad_storage`) is withdrawn, Google Analytics deletes user
  properties.
- *Decision*: all four defaults false; on each answer `setConsent` with `ANALYTICS_STORAGE`
  following the player's choice and the three ad types always `DENIED` (the game has no ads),
  then `setAnalyticsCollectionEnabled(choice)`.

## 4. Keeping the SDK quiet before consent

- `firebase_analytics_collection_enabled=false` disables collection until
  `setAnalyticsCollectionEnabled(true)`; `firebase_analytics_collection_deactivated=true` would
  disable it for good (not used: players may opt in).
- `google_analytics_adid_collection_enabled=false` stops advertising ID collection; the
  Analytics SDK still merges the `com.google.android.gms.permission.AD_ID` permission into the
  manifest, so we remove it with a `tools:node="remove"` merge rule (our inference from the
  manifest merger; without the permission the ad ID reads as zeros). In Play Console's
  advertising ID declaration, answer that the app doesn't use it.
- `google_analytics_ssaid_collection_enabled=false` stops Android ID collection (documented by
  React Native Firebase's manifest; not found in Google's own page *(re-check)*).
- The `FirebaseInitProvider` content provider starts Firebase at process start; removing it
  (`tools:node="remove"`) is supported as long as `FirebaseApp.initializeApp(context)` runs
  before any Firebase use. *Decision*: removed; `FirebaseTelemetry` calls `initializeApp` on the
  player's first "yes", so no Firebase code runs before consent (the CNIL asks publishers to
  check that no tracker request leaves before consent, §6).

## 5. What Firebase collects (for the privacy policy and Data safety)

- Analytics: an app instance id (resettable with `resetAnalyticsData()`, which clears the
  on-device analytics data), events and their parameters, user properties, device and app
  metadata (model, OS version, language, app version), coarse location derived from the IP
  address. Deleting a Firebase Installation ID doesn't delete Analytics data; server-side
  deletion goes through the Google Analytics User Deletion API (Admin API
  `properties.submitUserDeletion`). User-level data retention is a property setting (up to 14
  months; GA4 also offers a shorter one, *re-check*): **choose the shortest.**
- Crashlytics: stack traces, app state, device metadata, a Crashlytics installation UUID, and
  anything the app attaches (custom keys, logs: here only the screen name and the area of a
  handled problem). Firebase's privacy page states Crashlytics' retention (90 days at the time
  of writing, *re-check*).
- Firebase Analytics and Crashlytics process data as Google's service; transfers to the US rely
  on Google LLC's certification under the EU-US Data Privacy Framework (adequacy decision of
  July 2023) and Google's data processing terms; accept them in the Firebase console
  (*Project settings > Privacy*).

## 6. GDPR, ePrivacy and the CNIL's rules for apps

- **Scope.** Article 82 of the French Loi Informatique et Libertés (ePrivacy Directive art.
  5(3)) requires consent before reading or writing information on a user's device, unless it is
  strictly necessary for a service the user explicitly asked for. The EDPB's Guidelines 2/2023
  (v2, Oct 2024) read this broadly: it is not limited to cookies and covers any storage or
  access on a connected device, which includes SDKs in apps. The CNIL's mobile app
  recommendation (Sept 2024, English version 2025) makes the publisher responsible (at least
  jointly) for the SDKs it embeds and asks it to verify that no tracking request is sent before
  consent.
- **Analytics needs consent here.** The CNIL's audience-measurement exemption only covers tools
  strictly limited to the publisher's own anonymous statistics, without cross-tracking or
  passing data to third parties for their own purposes; Google Analytics for Firebase isn't set
  up like that, so consent it is.
- **Crash reports.** No CNIL position specific to crash-reporting SDKs was found. The exemption
  is for what is strictly necessary to a service the user requested, and the CNIL recommends
  that exempt trackers serve one purpose only. Crashlytics also reads device information and
  stores an installation UUID for the developer's own debugging. *Decision*: opt-in, as its own
  switch, the safe reading.
- **Valid consent.** Prior, free, specific, informed and unambiguous; continuing to use the app
  isn't consent. Refusing must be as easy as accepting: on the same screen, with the same ease
  (the CNIL names "accept all"/"refuse all" buttons of the same format as a clear way to do it);
  boxes are unticked by default. The user must be able to withdraw at any time, as simply as
  consent was given, from a place that stays reachable. Keeping a choice (consent or refusal)
  for about six months is the CNIL's good practice, then asking again is allowed but not
  required. *Decision*: two identical buttons, a "Choose for each" panel with both switches off,
  Settings switches always available; the choice is kept until changed and only asked again
  when the question changes. The time of the answer is kept on the device as proof.
- **Minors.** In France a minor can consent alone to an information society service from 15
  (art. 45 of the Loi Informatique et Libertés, using GDPR art. 8); under 15, consent must be
  given jointly by the minor and a holder of parental authority. See §8.
- **Enforcement.** The CNIL fined Fnac Darty / Fnac Direct €7 million (22 Oct 2026) for a
  tracker set without consent in a mobile app.

## 7. Google Play

- **Data safety form.** Every app declares what it collects and shares, why, whether
  collection is optional, whether data is encrypted in transit, and how to request deletion,
  including what its SDKs do; it must match the privacy policy. Crash logs and diagnostics are
  "App info and performance"; app-specific ids, the Firebase installation id or an advertising
  id are "Device or other IDs". Data can be declared optional only if every user can opt in or
  out; "encrypted in transit" only if it applies to everything the app and its SDKs send.
  Proposed answers: `docs/play-data-safety.md`.
- **Account deletion.** Apps that let users create an account must offer both an in-app path to
  delete the account and its data, and a web link where deletion can be requested (it must work,
  feature the deletion path prominently and name the app or developer; a simple request form is
  enough). Deactivating or freezing doesn't count; data kept for legal or security reasons must
  be explained in the privacy policy. Deep Line's player id and secret make an account (gifts,
  cloud saves), so this applies. *Built*: Settings > Privacy > *Delete my online account* (with
  a confirmation) calls `POST /api/account/delete` (id + secret), which deletes the player's file
  (secret hash, cloud save, waiting gifts) and calls the `AccountStore.onDelete` listeners for any
  other per-player data; the game then switches to a new random identity. The battle server
  also serves `GET/POST /delete-account`, a page that deletes the account from the recovery code
  (shown in the game under Shop > Account), with an email fallback for players without it.
- **Advertising ID declaration**: answer that the app doesn't use one (§4).

## 8. Children and families: the audience question

- Apps whose audience includes children must follow the Families policies: only SDKs approved
  for child-directed services (unless behind a neutral age screen, or used so that no data is
  collected from children), no linking of the advertising ID to persistent identifiers for
  analytics, and more. The self-certified SDK program covers ad SDKs only; Firebase Analytics'
  status for child-directed use wasn't found *(re-check if children are targeted)*.
- **Assumption made here: Deep Line is not designed for children.** Play Console target audience
  13+ (13-15, 16-17, 18+), no ads, no personalised anything. **The developer must confirm this.**
  Caveats to weigh:
  - The cute, cartoon look may make Play consider that the game appeals to children; if Play
    asks to include under-13s, the Families policies apply (our setup already avoids the
    advertising ID, ads and any collection before consent, but analytics from children would need
    a neutral age screen that keeps it off for them).
  - France's age of digital consent is 15: a 13- or 14-year-old can't consent alone to the
    analytics. Options: target 16+ (simplest legally), or add a neutral age question before the
    consent sheet that keeps both switches off (and hides the sheet) for players under 15, or
    accept the residual risk (pseudonymous, ad-free statistics, off by default).

## 9. Points left open (for the developer)

- **Firebase project and links**: create it, add `app/google-services.json`, accept the data
  processing terms, set the shortest data retention, link Google Play (`docs/firebase-setup.md`).
- **Privacy policy URL**: publish `docs/privacy-policy.md` (completed) on a web page and build
  release apps with `-PdeeplinePrivacyPolicyUrl=https://...`; the placeholder
  (`https://deepline.example/privacy`) goes nowhere.
- **Web deletion link**: deploy the battle server behind HTTPS and give Play
  `https://<your-server>/delete-account`; set `DEEPLINE_PRIVACY_CONTACT` (and
  `DEEPLINE_PRIVACY_POLICY_URL`) on the server so the page shows how to reach you.
- **Game server contact at launch**: the app asks the server for waiting gifts at every launch,
  which registers every player's random id and a hash of its secret (and shows the server their
  IP address), even players who never play online. It's first-party and functional, but the
  data-minimising choice is to only check gifts once the player has used an online feature (gifts
  can only come from online opponents). That would also let the Data safety form call these ids
  optional.
- **Retention on the server**: accounts are kept until deleted. Consider deleting accounts
  inactive for a long time (say 24 months) and say so in the privacy policy. A gift sent to a
  deleted id recreates a file holding only that gift (and the sender's display name).
- **Audience** (§8).

## Sources

Firebase and Google
- [Configure Analytics data collection and usage (Android)](https://firebase.google.com/docs/analytics/android/configure-data-collection)
- [Set up consent mode for apps (Android)](https://developers.google.com/tag-platform/security/guides/app-consent?platform=android)
- [FirebaseAnalytics reference (Android)](https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics) and [ConsentBuilder](https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/ConsentBuilder)
- [Verify and update consent settings in Google Analytics](https://support.google.com/analytics/answer/14275483?hl=en)
- [Events: Games (Firebase Help)](https://support.google.com/firebase/answer/9267565?hl=en) and [Recommended events](https://support.google.com/firebase/answer/9267735?hl=en)
- [FirebaseAnalytics.Event reference](https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics.Event)
- [Event collection limits](https://support.google.com/firebase/answer/9237506?hl=en)
- [Measure screenviews](https://firebase.google.com/docs/analytics/screenviews)
- [Measure in-app purchases (Android)](https://firebase.google.com/docs/analytics/android/measure-in-app-purchases)
- [Monitor events with DebugView](https://firebase.google.com/docs/analytics/debugview)
- [Customize crash reports for Android (opt-in reporting)](https://firebase.google.com/docs/crashlytics/android/customize-crash-reports) and [FirebaseCrashlytics reference](https://firebase.google.com/docs/reference/android/com/google/firebase/crashlytics/FirebaseCrashlytics)
- [Privacy and Security in Firebase](https://firebase.google.com/support/privacy), [Manage Instance ID data](https://firebase.google.com/support/privacy/manage-iids), [Clear and export end-user data](https://firebase.google.com/support/privacy/clear-export-data)
- [Privacy controls in Google Analytics (retention)](https://support.google.com/firebase/answer/9019185?hl=en)
- [Prepare for Google Play's data disclosure requirements (Firebase)](https://firebase.google.com/docs/android/play-data-disclosure)
- [Take control of your Firebase init on Android (FirebaseInitProvider)](https://firebase.blog/posts/2017/03/take-control-of-your-firebase-init-on)
- [Firebase Android release notes (BoM 35, minSdk 24)](https://firebase.google.com/support/release-notes/android)
- [React Native Firebase JSON config (SSAID flag)](https://rnfirebase.io/app/json-config)

Google Play
- [Provide information for Google Play's Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Understanding Google Play's app account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)
- [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- [Designing your account deletion experience (Android Developers Blog, 2024)](https://android-developers.googleblog.com/2024/03/designing-your-account-deletion-experience-google-play.html)
- [Google Play Families policies](https://support.google.com/googleplay/android-developer/answer/9893335?hl=en), [Data practices in Families apps](https://support.google.com/googleplay/android-developer/answer/11043825?hl=en), [Families Self-Certified Ads SDK Program](https://support.google.com/googleplay/android-developer/answer/9900633?hl=en)
- [Manage target audience and app content settings](https://support.google.com/googleplay/android-developer/answer/9867159?hl=en-GB)
- [Declare your app's data use (Android Developers)](https://developer.android.com/privacy-and-security/declare-data-use)

CNIL, EDPB, French law
- [Recommandation relative aux applications mobiles (CNIL, 2024)](https://www.cnil.fr/sites/cnil/files/2024-09/recommandation-applications-mobiles.pdf) and [English version](https://www.cnil.fr/sites/cnil/files/2025-05/recommendation-mobiles-app.pdf)
- [Applications mobiles : comment intégrer des SDK](https://www.cnil.fr/fr/applications-mobiles-comment-integrer-des-sdk-et-respecter-la-vie-privee-des-utilisateurs)
- [Recommandation cookies et autres traceurs, version consolidée (Jan 2026)](https://www.cnil.fr/sites/default/files/2026-01/recommandation_cookies_consolidee.pdf) and [FAQ](https://www.cnil.fr/fr/cookies-et-autres-traceurs/regles/cookies/FAQ)
- [Mesurer la fréquentation de vos sites web et de vos applications (exemption)](https://www.cnil.fr/fr/mesurer-la-frequentation-de-vos-sites-web-et-de-vos-applications)
- [Sanction Fnac (application mobile, 2026)](https://www.cnil.fr/fr/sanction-cookies-app-fnac)
- [EDPB Guidelines 2/2023 on the technical scope of Art. 5(3) ePrivacy (v2)](https://www.edpb.europa.eu/system/files/documents/2024-10/edpb_guidelines_202302_technical_scope_art_53_eprivacydirective_v2_en_0.pdf)
- [Article 45, Loi Informatique et Libertés (Légifrance)](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000037823135) and [CNIL: parental consent under 15](https://www.cnil.fr/en/recommendation-4-seek-parental-consent-children-under-15)
