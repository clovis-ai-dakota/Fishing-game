#!/usr/bin/env bash
# Makes the Google Play phone screenshots (docs/store/screenshots.md): 8 shots in each listing
# language, 1080 x 1920, from the game itself.
#   tools/store-screenshots/make-store-screenshots.sh [locale ...]   (default: all six)
# 1. StoreScreenshotsTest (Robolectric, one class per language) plays the scripted casts, renders
#    the app's screens and the captions in that language, and exports the casts' 3D frames;
# 2. compose.cjs draws the 3D frames with the game's shaders (WebGL preview), puts the HUD on top
#    and lays each shot out with its caption;
# 3. the result is written without alpha to docs/store/listings/<locale>/images/phoneScreenshots/
#    (<n>_<locale>.jpg: high-quality JPEG, which Play accepts, keeps the 48 shots small; set
#    STORE_SCREENSHOTS_PNG=1 for 24-bit PNGs instead).
# Needs the Android SDK (the export runs as a unit test), Node with Playwright, and ffmpeg.
set -euo pipefail
cd "$(dirname "$0")/../.."

all=(en-US fr-FR es-ES de-DE pt-BR tr-TR)
locales=("$@")
[ ${#locales[@]} -eq 0 ] && locales=("${all[@]}")

class_of() {
  case "$1" in
    en-US) echo En ;; fr-FR) echo Fr ;; es-ES) echo Es ;; de-DE) echo De ;; pt-BR) echo Pt ;; tr-TR) echo Tr ;;
    *) echo "unknown locale $1" >&2; exit 1 ;;
  esac
}

tests=()
for locale in "${locales[@]}"; do tests+=(--tests "*StoreScreenshots$(class_of "$locale")Test"); done
DEEPLINE_STORE_SCREENSHOTS=1 ./gradlew :app:testDebugUnitTest "${tests[@]}" --max-workers=3

node tools/store-screenshots/compose.cjs "${locales[@]}"

for locale in "${locales[@]}"; do
  out="docs/store/listings/$locale/images/phoneScreenshots"
  mkdir -p "$out"
  rm -f "$out"/*.png "$out"/*.jpg
  for n in 1 2 3 4 5 6 7 8; do
    src="app/build/store-screenshots/$locale/out/$n.png"
    if [ "${STORE_SCREENSHOTS_PNG:-0}" = 1 ]; then
      ffmpeg -y -loglevel error -i "$src" -pix_fmt rgb24 "$out/${n}_${locale}.png"
    else
      # 4:4:4 chroma and a high quality: no blur on the HUD's small text or the coloured edges.
      ffmpeg -y -loglevel error -i "$src" -pix_fmt yuvj444p -q:v 2 "$out/${n}_${locale}.jpg"
    fi
  done
done

for locale in "${locales[@]}"; do
  out="docs/store/listings/$locale/images/phoneScreenshots"
  for f in "$out"/*; do
    printf '%s  %s\n' "$(ffprobe -v error -select_streams v:0 -show_entries stream=width,height,pix_fmt -of csv=p=0 "$f")" "$f"
  done
done
du -ch docs/store/listings/*/images/phoneScreenshots/* | tail -1
