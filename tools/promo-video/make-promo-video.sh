#!/usr/bin/env bash
# Makes the Play promo video (1920 x 1080, 25 fps, about 24 s) from real casts drawn by the
# game's own 3D renderer, with the game's CC0 music and sounds (credits: CREDITS.md here).
#   tools/promo-video/make-promo-video.sh [locale ...]     (default: en-US)
# Writes app/build/promo-video/deep-line-promo-<locale>.mp4 (git-ignored build output: upload it
# to YouTube and keep a copy outside the repository; see README.md here).
# Needs the Android SDK (the export runs as a unit test), Node with Playwright, and ffmpeg.
set -euo pipefail
cd "$(dirname "$0")/../.."

# 1. Export the frames: a full cast steered by the Hard rival's brain, then the dark with a Lantern.
DEEPLINE_PROMO_VIDEO=1 ./gradlew :app:testDebugUnitTest --tests '*StoreArtExportTest*' --max-workers=2

# 2. Draw, frame and encode, once per language (captions from docs/store/listings/<locale>/).
locales=("$@")
[ ${#locales[@]} -eq 0 ] && locales=(en-US)
for locale in "${locales[@]}"; do
  node tools/promo-video/make-promo-video.cjs --locale "$locale"
done
