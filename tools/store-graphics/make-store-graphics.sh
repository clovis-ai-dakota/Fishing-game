#!/usr/bin/env bash
# Regenerates the store graphics in docs/store/graphics/ from the game itself:
#   feature-graphic.png  1024 x 500, 24-bit PNG without alpha (Play's requirement), the 3D scene
#                        exported by StoreArtExportTest and drawn by the WebGL preview + the title;
#   play-icon-512.png    512 x 512 32-bit PNG, the adaptive launcher icon's layers drawn full
#                        square by StoreIconTest (Play applies the rounded mask and shadow).
# Also leaves app/build/store/launcher-icon-preview.png (circle, rounded square, themed icon).
# Needs the Android SDK (for the Robolectric tests), Node with Playwright, and ffmpeg.
set -euo pipefail
cd "$(dirname "$0")/../.."

./gradlew :app:testDebugUnitTest --tests '*StoreArtExportTest*' --tests '*StoreIconTest*' --max-workers=2

out=docs/store/graphics
mkdir -p "$out"
node tools/store-graphics/feature-graphic.cjs app/build/store-art/feature_graphic.json app/build/store-art/feature-graphic-rgba.png
# Drop the alpha channel: Play takes JPEG or 24-bit PNG for the feature graphic.
ffmpeg -y -loglevel error -i app/build/store-art/feature-graphic-rgba.png -pix_fmt rgb24 "$out/feature-graphic.png"
cp app/build/store/play-icon-512.png "$out/play-icon-512.png"

ffprobe -v error -select_streams v:0 -show_entries stream=width,height,pix_fmt -of csv=p=0 "$out/feature-graphic.png"
ffprobe -v error -select_streams v:0 -show_entries stream=width,height,pix_fmt -of csv=p=0 "$out/play-icon-512.png"
ls -l "$out"
