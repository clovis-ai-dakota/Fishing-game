# Promo video

A 24-second landscape video for the Play store listing (Play plays the promo video from YouTube,
in landscape), made only from the game itself:

- **Frames**: real casts, simulated by the game's rules and steered by the computer rival's brain
  (`BotBrain`, Hard), exported by `StoreArtExportTest` (`DEEPLINE_PROMO_VIDEO=1`) and drawn with the
  game's own shaders by the WebGL preview's `drawFrame` (`tools/render3d-preview/render.cjs`).
  Shot list: the descent through the sunlit shallows and the reef, the ascent hooking fish, a cut
  to the dark below 300 m with a Lantern, then an end card on the boat at the surface.
- **Layout**: the portrait game view (608 x 1080) in the middle of a 1920 x 1080 frame, the game's
  name and the store's screenshot captions on the left (`docs/store/listings/<locale>/screenshot_captions.txt`),
  the cast's real depth, bumps, fish and points on the right.
- **Sound**: the game's own music, CC0 (the shallows theme crossfading into the deep theme at the
  cut, like in the game), and its sound effects at the cast's real events (splash, catches, the
  Lantern, the surface, the results jingle). Credits below.

## Make it

```bash
tools/promo-video/make-promo-video.sh            # English
tools/promo-video/make-promo-video.sh fr-FR de-DE # other listing languages
```

Output: `app/build/promo-video/deep-line-promo-<locale>.mp4` (H.264 + AAC, yuv420p, faststart,
about 6 MB). Build output is git-ignored; the English video made for the launch kit is also kept
outside the repository at **`/home/user/deep-line-launch-kit/deep-line-promo-en-US.mp4`**.

## Upload

1. YouTube: upload the MP4, visibility **Unlisted** (or Public), **no monetisation** (Play refuses
   videos with ads), not age-restricted, not "made for kids" unless the audience decision says so.
2. Play Console > Main store listing > **Video**: paste the video's URL (`https://www.youtube.com/watch?v=...`,
   not a playlist or a channel). A feature graphic must exist for the video to show.
3. Translated listings can use the localised videos (one URL per language), or share the English one.

## Credits (all CC0 1.0, from OpenGameArt.org; see `app/src/main/assets/audio/CREDITS.md`)

- Music: "Drifting" by Some Weirdo (shallows), "Deep Sea" by Umplix (deep).
- Sounds: Fisheefects by You're Perfect Studio (sounds by Memoraphile): splash, catch, rare catch;
  40 CC0 water/splash SFX by rubberduck: surface splash; Interface Sounds by Kenney: lantern;
  85 Short music jingles by Kenney: results.

CC0 asks for no credit; the video description on YouTube can carry the line above anyway.
