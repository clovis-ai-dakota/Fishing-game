# Game feel: sound, music, haptics and juice

Research notes for Deep Line's game feel workstream: what the industry recommends, what we
decided, and where it lives in the code. Sources are listed at the end. "Read" means the page was
read in full for this note; "via search" means only a search summary was available (some sites
could not be reached from the build machine).

## 1. Audio mixing on mobile

**Findings.** Phones play through small speakers or earbuds, often at low volume, often muted.
Sound effects must carry information on their own but never be required (many people play
silently), so everything audible also has a visual. A game mixes a few buses (music, effects,
UI) and exposes their levels; accessibility guidance asks for separate music and effects volumes
at least. Short effects are pre-decoded so they play with low latency; on Android that is
`SoundPool`, which decodes to PCM once (each sound limited to 1 MB decoded, about 5.6 s of 44.1 kHz
stereo), plays at a rate of 0.5 to 2.0 and steals streams by priority then age when `maxStreams`
is exceeded. Music is streamed (`MediaPlayer`), not decoded up front.

**Decisions.**
- Three levels: master, music, sound effects (0 to 100 %), plus haptics and reduce motion
  (`feel/FeelSettings.kt`, stored in the device key-value store, not in saved progress).
- Effects: one `SoundPool` (12 streams, usage GAME / content SONIFICATION), files peak-normalised
  to -1 dBFS so a single per-sound gain in the `Sfx` table sets the mix. Music: one looping
  `MediaPlayer` per track (usage GAME / content MUSIC), loudness-normalised to -20 LUFS so the
  layers sit at the same level.
- Every sound has a visual counterpart already (notices, flashes, bump rings), so a muted game
  loses nothing.

## 2. Adaptive, layered music

**Findings.** Two standard techniques: *vertical layering* (stems on a shared timeline faded in
and out to change intensity) and *horizontal re-sequencing* (switching between sections at
musical boundaries). Middleware such as Wwise and FMOD implements both; layering is used for
intensity, re-sequencing for bigger changes of state. Crossfades must be slow enough not to draw
attention (seconds, not frames).

**Decisions.** We have no stems (CC0 tracks are finished mixes), so we use the layering idea with
mood tracks: a menu track, and during a cast a bright, calm "shallows" layer that crossfades into
a dark "deep" layer as the hook goes down (none of the dark layer until 150 m above the dark
water, all of it 50 m into it; `GameFeel.musicDepthMix`). `MusicMixer` glides each track's level
towards its target over 2.5 s; tracks at zero are paused. Each file is a seamless loop: the last
4 s of the track are mixed over its first 4 s at build time, so `MediaPlayer`'s loop point falls
in continuous material.

## 3. Sound effect variation and voice limiting

**Findings.** Identical repeats sound mechanical ("machine-gun effect"). The usual cures: several
recordings per sound, never the same one twice in a row, and small random pitch and volume
offsets per play (around ±5 % pitch is a common default); random pitch suits noises, not tuned
UI or musical cues. Middleware limits voices per sound (and globally) so dense moments don't
pile up the same sound.

**Decisions.** `SfxMixer` (pure, unit tested) picks a file at random without repeats, applies the
sound's pitch and volume jitter (bubbles and splashes vary most; jingles, catch chimes and the
secret sound have none), drops a repeat sooner than the sound's `minGapMs`, and stops the oldest
voice beyond `maxVoices`. On top of that, game meaning drives pitch: each bump of a cast is a
little lower and louder (the line is running out), the reel's clicks drop with a heavy line,
bubbles drop with depth, and catches in quick succession climb a semitone each (up to a fifth).

## 4. Audio focus, ducking and the background on Android

**Findings (read).** Request focus right before playing and check the result; use
`AudioFocusRequest` (API 26+) with the same `AudioAttributes` as the player (usage GAME for games),
or the older `requestAudioFocus(listener, STREAM_MUSIC, AUDIOFOCUS_GAIN)` on API 24-25. On
`AUDIOFOCUS_LOSS` pause and don't resume by yourself; on `LOSS_TRANSIENT` pause and resume on
`GAIN`; on `LOSS_TRANSIENT_CAN_DUCK` lower the volume (from Android 8 the system ducks for you
unless you ask to pause instead). Android 12+ enforces it (fades out apps that lose focus) and
Android 15 refuses focus to apps that aren't on top. Google Play's core app quality list asks apps
to request and abandon focus, react to other apps' focus, and resume or show paused audio when
coming back from the background.

**Decisions.** `SoundPoolAudioEngine` asks for `AUDIOFOCUS_GAIN` (delayed gain accepted) only when
music is about to play and the app is on screen; pauses the music on a loss, ducks to 25 % when
asked, resumes on gain. Going to the background (`MainActivity.onStop`) pauses every effect and
the music and abandons focus; `onStart` resumes. The engine starts "in the background", so
nothing plays and no focus is requested before the activity is visible. If another app is
already playing music when ours would start, the game keeps its music off and only plays
effects (the courtesy most mobile games show to players' own playlists).

## 5. Haptics

**Findings (read).** Android's haptics principles: prefer *clear* haptics (crisp, short, like a
button) and *rich* ones (sequences of primitives) over *buzzy* ones (long ringing vibrations);
"given the choice of buzzy haptics or no haptics for touch feedback, choose no haptics". Use
predefined effects and `VibrationEffect.Composition` primitives where available, check support
(`areAllPrimitivesSupported`; a composition with one unsupported primitive doesn't play at all),
avoid legacy one-shots (they ring on cheap motors), match strength to importance and frequency,
stay consistent, and design haptics together with sound and visuals. Primitives: CLICK,
QUICK_RISE, SLOW_RISE, QUICK_FALL, TICK (API 30), THUD, SPIN, LOW_TICK (API 31); predefined
TICK, CLICK, HEAVY_CLICK, DOUBLE_CLICK (API 29).

**Decisions.** `feel/Haptics.kt`: eight effects from TICK to CATCH_EPIC, used only for bumps (a
light click; a thud for the last bump), catches (click, double click for rare and Sparkling, a
rise and thud for the legendary), the fast reel (spin), claims and battle wins. Ordinary taps
never vibrate. Fallbacks by API level: compositions (30+, when every primitive is supported),
predefined effects (29), short one-shots with amplitude where the motor allows (26-28), and plain
short pulses on 24-25 with the two lightest effects skipped. Effects closer than 60 ms are
dropped. On API 33+ they use `VibrationAttributes.USAGE_MEDIA`, so the phone's media vibration
intensity applies. The Vibration switch turns them all off; it explains when a phone has no
vibrator.

## 6. Screen shake

**Findings (via search).** Squirrel Eiserloh's GDC 2016 talk "Math for Game Programmers: Juicing
Your Cameras With Math" popularised *trauma*-based shake: events add trauma (0 to 1), trauma
decays linearly, the shake is trauma squared or cubed (small knocks barely register, big ones
really do), and the offset comes from smooth noise (Perlin-like) rather than white noise, so it
wobbles rather than jitters. Engines' examples (e.g. Bevy's 2D screen shake) follow it. Keep it
small: big shake nauseates.

**Decisions.** `feel/ScreenShake.kt` (unit tested): trauma capped at 1, decays at 1.6 per second,
shake = trauma², offsets from 1D value noise at 22 Hz, at most 1.8 % of the screen width at full
trauma. Trauma: 0.32 for a bump, 0.55 for the last bump, 0.6 for a snag, 0.3 to 0.5 for rare
catches (all in `GameConfig.Feel`). **Only the drawing moves**: in 2D the scene is translated
(the background and darkness bleed past the edges so no unlit strip shows); in 3D the
projection gets a screen-space translation, so every depth moves by the same pixels as the 2D
overlay, and the darkness centre (`uHookScreen`) moves with the drawn hook. The camera's
mapping of the play plane, the hook, the fish, steering input and hit detection never change
(`Render3dTest.theShakeMovesOnlyThePicture`, `GameFeelTest.bumpsShakeTheDrawingNotTheGame`).

## 7. Hit-stop, slow motion and fairness

**Findings (via search).** Hit-stop is a brief freeze on impact (tens of milliseconds; a few
frames for light hits, around 100 ms for heavy ones), applied to everything so it reads as weight
rather than a hiccup, and fired once per impact. Slow motion stretches a highlight.

**What the game's timing depends on.** `CastSession` runs on cast time (`elapsedSeconds`, the sum
of the `dt` it is given). Secrets that use time (Pinball, Corner pocket, Buzzer beater) read cast
time. Battles count cast time too: the 3-Minute Rush clock is `elapsedSeconds`, and online the
server validates the reported `durationSec` and bump times against the cast, not the wall
clock. So slowing `dt` changes no rule and no score, but it does give the player more real time
to react while it lasts.

**Decision.** A rare, Sparkling or legendary catch starts a hit-stop (0.06 s at 5 % speed), then
slow motion at 35 % easing back to normal over 0.45 s: about a quarter of a second of extra real
time per rare catch. It only plays in **solo casts**: in every battle (computer, pass and play,
online) the cast always runs in real time, so nobody gets more reaction time than their opponent,
and the online server sees exactly the casts it saw before (`GameFeelTest.slowMotionOnlyInSoloCasts`).
In solo play it can't change difficulty or the economy measurably (it triggers on the way up
after a rare catch, never during the descent that `DifficultyTest` tunes). The renderers'
animation clock slows with the game so the moment reads as one.

## 8. Particles and flashes ("juice it or lose it")

**Findings (via search).** Martin Jonasson and Petri Purho's "Juice It or Lose It" (GDC Europe
2012) and Jan Willem Nijman's "The Art of Screenshake" (2013) show how much feedback (particles,
tweening, shake, sound) adds to the same mechanics: maximum output for minimum input. Flashes
should be short and rare (photosensitivity guidance: no more than three flashes a second).

**Decisions.** `feel/Particles.kt`: seeded, stateless bursts (the same burst always looks the
same; nothing stored per particle), in world meters: the entry splash where the line meets the
water (spray falling back, foam spreading, a trail of bubbles), a puff of bubbles on every bump,
a stream of bubbles (and gold glints for Sparkling fish) on catches, and a splash when the line
breaks the surface. Both renderers draw them **before the darkness layer** (3D: in the sprite
batch; 2D: before the darkness rect), in the colour script's `splash` colour, which gets dimmer
and greener with depth, so they never light dark water (`Render3dTest.gameFeelIsDrawnUnderTheDarkness`).
The flash (warm white, gold for Sparkling and legendary fish) is a tint over the scene that
starts at 45 % and fades in 0.3 s, also under the darkness: in dark water only the lit circle
flashes.

## 9. Accessibility

**Findings (via search).** The Game Accessibility Guidelines and Xbox Accessibility Guideline 117
ask for separate volume controls (music, effects), a way to turn off or reduce camera shake and
other motion, and avoiding or limiting flashing; honouring the OS's reduce-motion setting as a
default is good practice. Haptics should be optional.

**Decisions.** Settings > Sound and feel has the three volumes, Vibration, and **Reduce motion**,
which turns off shake, slow motion and flashes (particles stay: they don't move the screen). Its
default follows the phone's "remove animations" setting (`ANIMATOR_DURATION_SCALE` = 0) until the
player chooses. Volume sliders save when let go and also shortly after any change, because
accessibility services set a slider's value without a drag (`SoundSettingsUiTest`). Every
change is logged as `settings_change` with the setting's name.

## 10. Audio sources (CC0 only)

The user chose CC0 sample packs. Kenney's site blocks automated downloads, so every file came from
OpenGameArt.org, where each page's licence was checked (CC0; Fisheefects is offered under CC0 among
other licences and is used under CC0). Downloads went to a scratch folder, were treated as data
(only listed, unzipped and read by ffmpeg), and only the files used were converted into the app:
38 mono OGG Vorbis effects and 3 stereo loops, 1.2 MB in total (`assets/audio`). Choosing without
listening: jingles were sorted by melodic direction measured with a pitch tracker (rising for wins,
falling for losses, level for draws) and every candidate's length, loudness and brightness were
measured. Credits: `app/src/main/assets/audio/CREDITS.md`, `audio/AudioCredits.kt` and Settings >
Credits; `AudioAssetsTest` fails if a file isn't credited or isn't CC0.

## Sources

- Android Developers, Haptics design principles (read): https://developer.android.com/develop/ui/views/haptics/haptics-principles
- Android Developers, Create custom haptic effects (read): https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects
- Android Developers, Manage audio focus (read): https://developer.android.com/media/optimize/audio-focus
- Android Developers, Core app quality guidelines, audio items (read): https://developer.android.com/docs/quality-guidelines/core-app-quality
- Android Developers, SoundPool reference (read): https://developer.android.com/reference/android/media/SoundPool
- Squirrel Eiserloh, "Math for Game Programmers: Juicing Your Cameras With Math", GDC 2016 (via search): https://gdcvault.com/play/1023146/Math-for-Game-Programmers-Juicing and https://www.gamedeveloper.com/programming/video-sprucing-up-cameras-with-math
- Bevy, 2D screen shake example following the trauma model (via search): https://bevy.org/examples/camera/2d-screen-shake/
- Martin Jonasson and Petri Purho, "Juice It or Lose It", GDC Europe 2012 (via search): https://gdcvault.com/play/1016487/Juice-It-or-Lose and https://www.gamedeveloper.com/design/video-is-your-game-juicy-enough-
- Hit-stop durations and practice (via search): https://uhiyama-lab.com/en/notes/ue/game-feel-hit-feedback/ and https://www.wayline.io/learn/game-feel/1
- Vertical layering and horizontal re-sequencing (via search): https://sounddesign.irpr.agency/glossary/vertical-layering/ and https://sounddesign.irpr.agency/glossary/horizontal-resequencing/
- Randomising sounds, pitch variation and no-repeat (via search): https://github.com/jackyyang09/Simple-Unity-Audio-Manager/wiki/9.-Randomizing-Sounds
- Voice limiting in middleware (via search): https://construct.net/en/forum/construct-2/general-discussion-17/limiting-audio-voices-126304
- Xbox Accessibility Guideline 117, visual distractions and motion settings (via search): https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/117
- Game Accessibility Guidelines (via search): https://gameaccessibilityguidelines.com/
- Unity, accessibility basics (separate volume sliders) (via search): https://unity.com/blog/video-game-accessibility-basics
- Bugnet, accessibility testing for indie games (flash limits, reduce flashing) (via search): https://bugnet.io/blog/accessibility-testing-for-indie-games
- Audio packs and tracks: see `app/src/main/assets/audio/CREDITS.md`.
