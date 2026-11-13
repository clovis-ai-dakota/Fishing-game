# Screenshot plan (phone)

The plan the localised screenshot step follows. Eight portrait shots, **core loop first**: the
first three must explain the game on their own (most people never scroll further). Same order,
same scenes, in every language; only the caption and the app's own text change.

Captions are single-sourced in `listings/<locale>/screenshot_captions.txt` (one line per shot, in
order) and repeated in the tables below; `StoreListingTest` checks both match and that each
caption is at most 32 characters.

## Format

- **1080 × 1920 px** (9:16), 24-bit PNG **without alpha** (or JPEG). Play accepts 320 to 3840 px
  per side with the long side at most twice the short one; 1080 px wide keeps the shots eligible
  for Play's larger placements. Up to 8 phone screenshots per language.
- Layout: the caption in the top 18% of the image on a band in the menu palette (Lagoon day for
  shots 1 to 3, the zone's own water colour behind the deep shots), white or dark text with 4.5:1
  contrast, the game's rounded sans-serif in bold, 64 to 80 px; the game screen below, slightly
  scaled down (about 88%) with rounded corners, never cropped through the HUD.
- Real game frames only (the 3D renderer: `Render3dExportTest` + `tools/render3d-preview`, or the
  Robolectric screenshot tests for menus), no mock-ups of features that don't exist, no device
  frame needed. Nothing that shows a fishing secret's name or explanation.
- Alt text (Play Console asks for it per image): use the "Alt text" column, translated with the
  captions.
- File names for fastlane: `listings/<locale>/images/phoneScreenshots/<n>_<locale>.jpg`
  (high-quality JPEG, 4:4:4 chroma, about 230 KB a shot and 11 MB for all 48; the same shots as
  24-bit PNGs would be about 40 MB. `STORE_SCREENSHOTS_PNG=1` makes `<n>_<locale>.png` instead).

## The shots

| # | Scene | Game state to set up | Alt text (English) |
| --- | --- | --- | --- |
| 1 | **The descent** in sunlit water: the hook steering between a rock and two fish, the finger ring visible, the boat at the top. | A fresh cast at 15 to 25 m, 4 bumps left on the HUD, a few colourful shallows species around the hook. | The hook sinks past fish and rocks under a small fishing boat. |
| 2 | **Bumps**: a bump on a jelly or a rock with the puff and the HUD's bump counter at 2 of 4. | Twilight Reef, 120 to 180 m, one bump just used (show the bump effect frame). | The hook bumps into a jelly; the counter shows two bumps left. |
| 3 | **The ascent**: the line turning up and a fish hooked by the head, two more already on the line, the weight bar half full. | Twilight Reef on the way up, catch chime moment. | A fish is hooked on the way up; the weight bar fills. |
| 4 | **Dark water**: below 300 m with the Lantern on, the lit circle around the hook, glowing anglers at its edge. | Midnight Trench, 340 to 380 m, Lantern on. Never a shot that reveals more than the lit circle shows in play. | In the dark below 300 m, a lantern lights a small circle around the hook. |
| 5 | **The results**: Cast complete with a Sparkling catch, points and depth multiplier. | Results screen after a good cast with a Sparkling fish (no secret line on it). | The results show a Sparkling fish and the points earned. |
| 6 | **The Dex**: the species grid with zone frames, caught cards, silhouettes and the completion ring. | A player with 40 to 60 species caught across three zones. | A grid of fish cards in the Dex, some caught and some still unknown. |
| 7 | **A battle**: an online or computer battle in Best of 5, both scores and the opponent's live depth. | Battle screen mid-match, round 3, close score. Use a generated name for the opponent. | A fishing battle against another player, with both scores. |
| 8 | **Make it yours**: the boat editor with a custom hull, sail and flag, or the skins preview. | Boat editor with a colourful free design (no legendary set shown). | The boat editor with a custom painted fishing boat. |

## Captions in every language

| # | en-US | fr-FR | es-ES |
| --- | --- | --- | --- |
| 1 | Steer your hook into the deep | Guidez l'hameçon vers le fond | Guía el anzuelo hacia el fondo |
| 2 | Dodge rocks, jellies and heads | Évitez rochers, méduses et têtes | Esquiva rocas, medusas y cabezas |
| 3 | Hook fish on the way back up | Ferrez les poissons en remontant | Engancha peces al subir |
| 4 | Light up the dark below 300 m | Éclairez le noir sous 300 m | Ilumina la oscuridad bajo 300 m |
| 5 | Rare fish pay big | Les raretés rapportent gros | Los peces raros valen mucho |
| 6 | Collect 200 species | 200 espèces à collectionner | Colecciona 200 especies |
| 7 | Battle friends or rivals online | Défiez amis et joueurs en ligne | Reta a amigos y rivales en línea |
| 8 | Design a boat that's yours | Un bateau à votre image | Diseña tu propio barco |

| # | de-DE | pt-BR | tr-TR |
| --- | --- | --- | --- |
| 1 | Lenk den Haken in die Tiefe | Guie o anzol até o fundo | İğneyi derinlere yönlendir |
| 2 | Weich Felsen und Quallen aus | Desvie de pedras e águas-vivas | Kayalardan, denizanalarından kaç |
| 3 | Fang Fische beim Aufstieg | Fisgue peixes na subida | Dönüşte balıkları yakala |
| 4 | Licht ins Dunkel unter 300 m | Ilumine o escuro abaixo de 300 m | 300 m altında karanlığı aydınlat |
| 5 | Seltene Fische bringen viel | Peixes raros valem muito | Nadir balıklar çok kazandırır |
| 6 | Sammle 200 Arten | Colecione 200 espécies | 200 türü topla |
| 7 | Duelle mit Freunden und online | Desafie amigos e rivais online | Arkadaşlarınla ve online düello |
| 8 | Dein eigenes Boot | Crie o seu próprio barco | Kendi tekneni tasarla |

## Making them

```bash
tools/store-screenshots/make-store-screenshots.sh              # all six languages (about 10 min)
tools/store-screenshots/make-store-screenshots.sh de-DE fr-FR  # some of them
```

Needs the Android SDK (the scenes run as Robolectric tests), Node with Playwright and ffmpeg. Then
look at every image before committing them (`montage` makes a quick contact sheet).

1. **`StoreScreenshotsTest`** (`app/src/test/.../StoreScreenshotsTest.kt`, one class per listing
   language, run only with `DEEPLINE_STORE_SCREENSHOTS=1`) starts the app in that language on a
   400 × 800 dp phone at 3x (1200 × 2400 px) with the store player (`StorePlayers`: about 50
   species caught across three zones, a boat designed from earned options, no secret found, the
   one-time "automatic translation" card dismissed, Reduce motion on so no shake or flash).
   - Shots 1 to 5 play the scripted casts of `StoreScenes` (`StoreScenes.kt`: hand-placed fish and
     obstacles at their real depths, a rope a player can buy, steered like a finger) frame by
     frame through the view model, as a cast does. At the shot's moment it saves the cast
     screen's own overlay and HUD on a transparent background (`Render3d.overlayOnly`: the 2D
     labels, aim ring, notices and results card, exactly what sits on the GL view on a phone) and
     the same moment as a 3D frame (`SceneBuilder`, `Render3dExport`). `StoreScenesTest` checks
     the scenes still show what the table says (depths, bumps left, load, Lantern, a Sparkling
     catch, no secret).
   - Shots 6 to 8 are the app's own screens (the Dex, a battle, the boat editor).
   - Captions are drawn by the test in the game's font (Roboto bold, 76 px, down to 64 px if a
     caption needs more than two lines), with the band's colours and the measured contrast in
     `shots.json`.
2. **`tools/store-screenshots/compose.cjs`** draws each 3D frame with the game's shaders (the
   WebGL preview's `drawFrame`), puts the HUD on top, and lays the shot out: the caption centred
   in the top 18% on the band (Lagoon's day sky; the Midnight Trench's water for shot 4), the
   phone's screen scaled to 950 px wide (88%) with rounded corners, a soft shadow and a thin
   light rim, starting just under the band and running off the bottom edge (the HUD and the top
   of every screen are never cropped).
3. The shell script writes the shots without alpha to `listings/<locale>/images/phoneScreenshots/`
   and prints their sizes and pixel formats.

What each shot shows (the same in every language; only the captions, the HUD and the app's text
change, species names stay English as in the game):

1. A fresh cast at 21 m in the Sunlit Shallows: 4 bumps left, the bump-free zone's chip, the
   opening hint, the aim ring beside the hook, a rock and shallows fish below.
2. 153 m in the Twilight Reef, a moment after the second bump on a jelly: the puff of bubbles,
   the notice "Bump! 2 left", two of four bumps lit.
3. 185 m on the way up after reaching the end of the rope: a Dusk Snapper hooked by the head with
   the catch sparkle, a Reef Grouper and a Sparkling Ribbon Eel already on the line, the load at
   9.2 of 18 kg.
4. 353 m in the Midnight Trench with a Lantern lit: the lit circle around the hook and glowing
   anglers at its edge, exactly as in play.
5. The results of the same cast as shot 3, played to the surface: five fish, the Sparkling Ribbon
   Eel at 3×, 567 points, no secret line.
6. The Dex: the completion ring (50 of 200, 25%), the zone counts, caught cards, one only seen
   (Reed Perch) and one silhouette.
7. A Best of 5 against the computer rival (Normal), after two rounds won one each, round 3 next.
8. The boat editor with the store player's design: a tangerine and navy striped hull, a seafoam
   triangle sail, a pink pennant and a star emblem, all earned with points or progress.

## Notes for the screenshot step

- The app's own text is in the shot's language (redo the shots when the UI or the captions change).
- The "300 m" in caption 4 is `GameConfig.DARK_DEPTH` (`StoreListingTest` checks it, the species count and
  the bump count against the game): if they change, change the listing too.
- Shot 7: online opponents' typed names must not appear (privacy); use the computer rival or a
  generated public name.
- Keep the feature graphic (`graphics/feature-graphic.png`) and shot 1 visually consistent: they
  sit side by side on the listing.
