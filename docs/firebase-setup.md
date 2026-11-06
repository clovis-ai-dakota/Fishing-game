# Setting up Firebase (analytics and crash reports)

Deep Line builds and runs without Firebase: until `app/google-services.json` exists, the
google-services and Crashlytics Gradle plugins aren't applied, no consent sheet is shown and
nothing is collected. These steps switch it on. Background and decisions:
`docs/research/analytics-privacy.md`; every event: `docs/analytics-events.md`.

## 1. Create the Firebase project

1. Go to the [Firebase console](https://console.firebase.google.com/) and click **Create a
   project** (or add Firebase to an existing Google Cloud project). Name it, for example,
   "Deep Line".
2. When asked about Google Analytics, keep it **enabled**, create (or pick) a Google Analytics
   account, and choose your country (France) for the reporting location and currency.
3. In **Project settings > General**, set the public-facing name and support email.
4. In **Project settings > Privacy** (or *Data privacy*), read and accept the Firebase data
   processing terms and the Google Analytics data processing terms, and fill in the
   data protection contact. This makes Google your processor under the GDPR.

## 2. Add the Android app

1. In the project overview, click the **Android** icon (*Add app*).
2. **Android package name**: `com.deepline.fishing` (exactly the `applicationId` in
   `app/build.gradle.kts`).
3. Nickname: "Deep Line Android". Debug signing certificate SHA-1: optional (not needed for
   Analytics or Crashlytics).
4. Click **Register app**, then **Download google-services.json**.
5. Put it at **`app/google-services.json`** (next to `app/build.gradle.kts`). Nothing else to
   edit: the plugin versions are already declared in the root `build.gradle.kts`, the libraries
   (Firebase BoM, `firebase-analytics`, `firebase-crashlytics`) in `app/build.gradle.kts`, and
   `app/build.gradle.kts` applies the plugins as soon as the file is there. Skip the console's
   "add the SDK" steps.
6. Committing the file is fine for a private repository (it isn't a secret: it identifies the
   project, and access is controlled by Firebase's rules). For a public repository, keep it out
   of git and add it on the build machine (CI secret), and restrict the API key in Google Cloud
   (*APIs & Services > Credentials*) to your package name and the Firebase APIs.
7. Build: `./gradlew :app:assembleDebug`. The unit tests keep working with the file present:
   they never start Firebase (`Telemetry.start` skips Robolectric).

## 3. Turn on Crashlytics

1. In the console, open **Run > Crashlytics** (or *Release & Monitor > Crashlytics*) and click
   **Enable Crashlytics**.
2. Crashlytics waits for the first report. Install a debug build on a phone, open it, tap
   **Allow** on the consent sheet (or turn on Settings > Privacy > Crash reports), then force a
   test crash: the simplest is a temporary `throw RuntimeException("Test crash")` in a button's
   `onClick` (remove it afterwards). `adb shell am crash com.deepline.fishing` also makes the
   running app crash on recent Android versions.

   Reopen the app: the report is sent at the next launch. It appears in the dashboard within a
   few minutes. A non-fatal report is also sent, for example, when the 3D renderer fails and
   the game falls back to 2D.
3. Release builds aren't minified today (`isMinifyEnabled = false`), so there's no mapping file
   to upload. If you turn on R8 later, the Crashlytics plugin uploads mapping files
   automatically for release builds.

## 4. Check the events in DebugView

Analytics normally batches events for about an hour. Debug mode sends them right away:

```bash
adb shell setprop debug.firebase.analytics.app com.deepline.fishing
# optional: see what the SDK logs
adb shell setprop log.tag.FA VERBOSE
adb shell setprop log.tag.FA-SVC VERBOSE
adb logcat -v time -s FA FA-SVC
```

1. Start the app, tap **Allow** on the consent sheet (no event is ever sent before that).
2. In the console, open **Analytics > DebugView** and pick your device: you should see
   `tutorial_begin`, then `screen_view` events as you move around, `level_start`/`level_end`
   for a cast, `earn_virtual_currency` after it, and so on (see `docs/analytics-events.md`).
3. Turn usage statistics off in Settings > Privacy: events stop.
4. When done: `adb shell setprop debug.firebase.analytics.app .none.`

Emulators are in debug mode automatically.

## 5. Settings in Google Analytics

In [Google Analytics](https://analytics.google.com/) (the property linked to the project):

- **Admin > Data collection and modification > Data retention**: choose the **shortest**
  retention for user-level data (the privacy policy says so; adjust it if you choose otherwise).
- **Admin > Data collection**: leave **Google signals** off and don't enable ads personalisation:
  the game has no ads, and Consent Mode always denies the ad types.
- **Admin > Data deletion requests** / the User Deletion API: how to erase a player's analytics
  data if they ask (they can find their app instance id only through support, so in practice
  answer with "Reset my statistics ID" in Settings > Privacy and the retention period).
- Optional: in **Admin > Custom definitions**, register the custom parameters you want as
  dimensions or metrics (`source`, `zone`, `depth_m`, `battle_mode`, `opponent`, `result`...).

## 6. Link Google Play

1. In the Firebase console: **Project settings > Integrations > Google Play > Link**, and pick
   the Play Console developer account and the app. (You need to be an owner of both.)
2. This lets Analytics record Google Play purchases and subscriptions as `in_app_purchase`
   automatically, with revenue: the game itself never logs revenue, so nothing is counted twice.
3. In Play Console, fill in **App content > Data safety** with `docs/play-data-safety.md`, and
   **App content > Advertising ID**: the app doesn't use it (the `AD_ID` permission is removed
   from the manifest).

## 7. Before releasing

- Build release apps with the published privacy policy:
  `./gradlew :app:bundleRelease -PdeeplinePrivacyPolicyUrl=https://your-site/privacy`
  (or put `deeplinePrivacyPolicyUrl=...` in `gradle.properties`). The default placeholder
  points nowhere.
- Give Play the account deletion web link: `https://<your-battle-server>/delete-account`
  (deploy the server behind HTTPS, and set `DEEPLINE_PRIVACY_CONTACT` and
  `DEEPLINE_PRIVACY_POLICY_URL` on it).
- Install the release build on a phone with an EEA locale and check, with the verbose logs
  above, that nothing is sent before the consent sheet is answered, nor after "Don't allow".

## Upgrading Firebase

The BoM is pinned to 34.19.0 because BoM 35 libraries are compiled with Kotlin 2.4 metadata,
which the project's Kotlin 2.2 compiler can't read. Upgrade Kotlin (2.3 or later) first, then
the BoM, the google-services plugin (root `build.gradle.kts`) and the Crashlytics plugin.
