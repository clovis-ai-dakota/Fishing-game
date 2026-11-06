# Google Play Data safety form: proposed answers

Proposed answers for Play Console > **App content > Data safety**, for the app as it is now
(Firebase Analytics and Crashlytics behind consent, the game server for online battles, gifts and
cloud save, Google Play Billing). Check each answer against the current form and against the
privacy policy before submitting: they must match. Re-check when a feature adds data (social
features, leaderboards, notifications...). Background: `docs/research/analytics-privacy.md`.

## Data collection and security

| Question | Answer | Notes |
| --- | --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** | Device or other IDs, app activity, app info and performance, purchase history (see below). |
| Is all of the user data collected by your app encrypted in transit? | **Yes, only if** the production game server is behind HTTPS/WSS | The default development URL is `ws://` (cleartext). Deploy with TLS (`wss://your-host/battle`, build with `-PdeeplineServerUrl=wss://...`); Firebase and Google Play always use TLS. If any player can still reach a `ws://` server, answer **No**. Consider removing `usesCleartextTraffic` from the release manifest. |
| Do you provide a way for users to request that their data is deleted? | **Yes** | In the app: Settings > Privacy > *Delete my online account*; on the web: `https://<your-server>/delete-account`; statistics and crash reports: switch off (clears the device's analytics data) and retention limits. |

## Account creation and deletion

| Question | Answer |
| --- | --- |
| Does your app allow users to create an account? | **Yes**: the game creates an account (random player ID and secret) on the server for gifts and cloud save. Choose the option for accounts created without a username/password from the user (an app-generated account) if the form offers it; otherwise "username and password / other". |
| Delete account URL | `https://<your-server>/delete-account` (served by the battle server: a form taking the recovery code, with your contact email for players who lost it). It names the app and features the deletion path, as Play requires. |
| In-app deletion | Settings > Privacy > *Delete my online account* (with a confirmation). |

## Data types

For each type: **collected** (sent off the device) or **shared** (transferred to a third party
for its own use). Data sent to a service provider processing it on your behalf (Firebase as your
processor, your hosting provider) counts as collected, not shared, per Play's definitions.

### Device or other IDs: collected, not shared

| | |
| --- | --- |
| What | The game's random player ID (server), Firebase's app instance ID (Analytics, if allowed), the Crashlytics installation UUID (if allowed). No advertising ID, no Android ID. |
| Optional? | **No** for the player ID (the game checks the server for gifts at every launch). If you change that to "only once the player uses an online feature", it becomes optional. |
| Ephemeral? | No |
| Purposes | App functionality (gifts, online battles, cloud save), Analytics (statistics, if allowed) |

### App activity > App interactions: collected, not shared

| | |
| --- | --- |
| What | Screens opened, casts and battles, tour progress, points earned and spent (Google Analytics for Firebase). |
| Optional? | **Yes**: only after the player allows usage statistics; off by default, can be turned off any time. |
| Ephemeral? | No |
| Purposes | Analytics |

### App activity > In-app search history, installed apps, other user-generated content

Not collected. (The battle name is sent to the server and the opponent for a match: if you
consider it user-generated content, declare **Other user-generated content: collected, App
functionality, required for online play**; it isn't stored.)

### App info and performance > Crash logs: collected, not shared

| | |
| --- | --- |
| What | Crash stack traces and handled errors (Crashlytics), with the current screen. |
| Optional? | **Yes**: only after the player allows crash reports. |
| Ephemeral? | No |
| Purposes | Analytics (app stability) |

### App info and performance > Diagnostics: collected, not shared

| | |
| --- | --- |
| What | Device model, Android version, app version sent with crash reports and analytics. |
| Optional? | **Yes** (same consent as above). |
| Purposes | Analytics |

### Financial info > Purchase history: collected, not shared

| | |
| --- | --- |
| What | Which shop items were bought (Analytics events without prices, if allowed; Google Analytics also records Google Play purchases with revenue once the Firebase project is linked to Play). Payment details are handled by Google Play and never reach the app. |
| Optional? | **Yes** (only with usage statistics allowed). |
| Purposes | Analytics |

If you'd rather not declare purchase history, don't link Firebase to Google Play and remove
`store_checkout`/`store_purchase` (then the shop item ids are still in
`spend_virtual_currency`, an app interaction).

### Location: approximate location

Google Analytics derives country/region from the IP address but doesn't collect a location from
the device. Google's own guidance for Firebase treats IP-derived location as not requiring a
separate declaration when it isn't stored as location data *(re-check
[Firebase's Play data disclosure page](https://firebase.google.com/docs/android/play-data-disclosure)
before submitting)*.

### Not collected

Personal info (name, email, address, phone), financial info other than purchase history,
health, messages, photos and videos, audio, files, calendar, contacts, web browsing, precise
location.

## Other Play Console declarations

- **Advertising ID**: the app does **not** use an advertising ID (the `AD_ID` permission is
  removed from the manifest; Analytics' ad ID collection is off).
- **Ads**: the app contains **no ads**.
- **Target audience and content**: see `docs/research/analytics-privacy.md` section 8 (the
  assumption is 13+, not designed for children; the developer must confirm).
- **Privacy policy URL**: the published version of `docs/privacy-policy.md`.
