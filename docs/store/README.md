# Store listing kit

Everything Play Console asks for on the store listing and App content pages, ready to paste or
upload. Research and sources: [`docs/research/launch.md`](../research/launch.md). Step-by-step
order: [`docs/release-checklist.md`](../release-checklist.md).

| File | What it is |
| --- | --- |
| `listings/<locale>/title.txt` | App name, at most 30 characters. |
| `listings/<locale>/short_description.txt` | Short description, at most 80 characters. |
| `listings/<locale>/full_description.txt` | Full description, at most 4000 characters. |
| `listings/<locale>/changelogs/1.txt` | "What's new" for versionCode 1 (the first release), at most 500 characters. Add `<versionCode>.txt` for each release. |
| `listings/<locale>/screenshot_captions.txt` | One caption per screenshot, in order (see `screenshots.md`). |
| [`screenshots.md`](screenshots.md) | The screenshot plan: 8 shots, core loop first, captions in every language. |
| [`keywords.md`](keywords.md) | Why these words, per language. |
| [`content-rating.md`](content-rating.md) | IARC questionnaire answers (one to confirm: wagers). |
| [`target-audience.md`](target-audience.md) | Target audience answers (**the Families/age decision is the developer's**). |
| [`data-safety.md`](data-safety.md) | Data safety checklist; full answers in `docs/play-data-safety.md`. |
| `graphics/feature-graphic.png` | Feature graphic, 1024×500, 24-bit PNG without alpha, rendered from the game's 3D renderer (`tools/store-graphics/`). |
| `listings/<locale>/images/phoneScreenshots/<n>_<locale>.jpg` | The 8 phone screenshots per language, 1080×1920, made from the game by `tools/store-screenshots/` ([`screenshots.md`](screenshots.md)). |
| `graphics/play-icon-512.png` | Play Store icon, 512×512 32-bit PNG, full square (Play rounds the corners). |

Locales: `en-US` (default language), `fr-FR`, `es-ES`, `de-DE`, `pt-BR`, `tr-TR`. Spanish is written
for Spain; copy it to `es-419` with the two word changes in `keywords.md` for Latin America.

## Checks

`./gradlew :core:test --tests '*StoreListingTest*'` checks every locale against Play's limits
(counting characters as Play does, not bytes), refuses ranking claims ("best", "#1", "top",
"popular", "free" and their translations) and emoji, checks that no fishing secret is named,
that the numbers quoted (200 species, 4 bumps, the dark at 300 m) match the game, and that the
captions match `screenshots.md`. It prints a table of the counts.

## Uploading

Paste into Play Console (*Grow users > Store presence > Main store listing*, then *Translations*
for each language), or use [fastlane supply](https://docs.fastlane.tools/actions/supply/): the
folders follow its layout (`fastlane supply --metadata_path docs/store/listings --skip_upload_apk
--skip_upload_aab`, with a Play Console service account). Graphics go in *Main store listing >
Graphics*: app icon, feature graphic, phone screenshots (`listings/<locale>/images/phoneScreenshots/`, see `screenshots.md`), promo video URL (YouTube,
unlisted is fine, ads off; the video file is described in `tools/promo-video/README.md`).

Contact details shown on the listing: an email address is required (use the privacy contact or
a dedicated one); a website is optional (the privacy policy's site works).
