# Data safety: reference for the listing

The full proposed answers are in [`docs/play-data-safety.md`](../play-data-safety.md); they must
match the published privacy policy ([`docs/privacy-policy.md`](../privacy-policy.md)). This page
is the checklist of what changed or must be true at launch.

## Must be true before submitting

- [ ] The production game server is behind **HTTPS/WSS** and the release bundle was built with
      `-PdeeplineServerUrl=wss://<host>/battle`. Release builds refuse cleartext traffic (network
      security config), so the answer to "Is all user data encrypted in transit?" is **Yes**.
- [ ] `https://<host>/delete-account` works (the **Delete account URL** in the form).
- [ ] The privacy policy is published and the release was built with
      `-PdeeplinePrivacyPolicyUrl=https://...` (Play Console > App content > Privacy policy, same URL).
- [ ] Firebase: if `app/google-services.json` is in the build, declare Analytics and
      Crashlytics data as in `docs/play-data-safety.md`; if it isn't, the "App activity", "App
      info and performance" and "Purchase history" rows don't apply (only the game server's
      player ID and the social data remain).

## What this launch kit added

- **In-app review** (Play In-App Review API): the rating and review are entered in Google
  Play's own card and handled by the Play Store under Play's terms; the app never receives them.
  Google's Play Core data safety note: "The data collected is used to leave a review on the Play
  Store." Nothing to declare for the app. The `review_prompt` analytics event (only with usage
  statistics allowed) is an app interaction already covered by "App activity > App interactions".
- No new data types, purposes or recipients: `ConsentStore.CONSENT_VERSION` stays 1.

## Quick answers (summary of the full document)

| Section | Answer |
| --- | --- |
| Collects or shares user data | Yes (collected, none shared) |
| Encrypted in transit | Yes (with the WSS server) |
| Deletion request | Yes: in the app (Settings > Privacy) and on the web (`/delete-account`) |
| Account creation | Yes, app-generated account (random player ID and secret) |
| Device or other IDs | Collected; player ID required, Firebase IDs optional; app functionality, analytics |
| App activity (interactions, other actions, user-generated content) | Collected; optional |
| App info and performance (crash logs, diagnostics) | Collected; optional |
| Financial info > Purchase history | Collected; optional (only with statistics and the Firebase-Play link) |
| Location, personal info, messages, photos, contacts... | Not collected |
