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
- File names for fastlane: `listings/<locale>/images/phoneScreenshots/<n>_<locale>.png`.

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

## Notes for the screenshot step

- The app's own text in the shots should be in the shot's language once the localisation work
  lands; until then, keep English UI and localised captions (acceptable, but redo them when the
  UI is translated).
- The "300 m" in caption 4 is `GameConfig.DARK_DEPTH` (`StoreListingTest` checks it, the species count and
  the bump count against the game): if they change, change the listing too.
- Shot 7: online opponents' typed names must not appear (privacy); use the computer rival or a
  generated public name.
- Keep the feature graphic (`graphics/feature-graphic.png`) and shot 1 visually consistent: they
  sit side by side on the listing.
