#!/usr/bin/env bash
# Checks a release build before it goes to Google Play (docs/release-checklist.md):
#   - version code and name, target SDK 36, not debuggable;
#   - cleartext traffic refused (network security config) and the game server on wss://;
#   - every 64-bit native library 16 KB aligned (ELF LOAD segments) and the APK zip-aligned
#     for 16 KB pages (zipalign -P 16);
#   - the bundle is signed (with the upload key) and carries the R8 mapping file.
# Usage: tools/release/check-release.sh [bundle.aab] [apk]
#   defaults: app/build/outputs/bundle/release/app-release.aab and the release APK
#   (build both with ./gradlew :app:bundleRelease :app:assembleRelease).
# Exits non-zero when a check fails. Needs the Android SDK build tools, readelf and unzip.
set -uo pipefail
cd "$(dirname "$0")/../.."

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)}}"
bt="$(ls -d "$sdk"/build-tools/* 2>/dev/null | sort -V | tail -1)"
aab="${1:-app/build/outputs/bundle/release/app-release.aab}"
apk="${2:-$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)}"
fail=0
ok() { echo "  ok    $*"; }
bad() { echo "  FAIL  $*"; fail=1; }
warn() { echo "  warn  $*"; }

echo "Bundle: $aab"
echo "APK:    ${apk:-none}"
[ -f "$aab" ] || { echo "No bundle: run ./gradlew :app:bundleRelease"; exit 1; }
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "Version and manifest"
if [ -n "$apk" ] && [ -x "$bt/aapt2" ]; then
  badging="$("$bt/aapt2" dump badging "$apk" 2>/dev/null)"
  echo "$badging" | sed -n "s/^package: name='\([^']*\)' versionCode='\([^']*\)' versionName='\([^']*\)'.*/  info  \1, versionCode \2, versionName \3/p"
  target="$(echo "$badging" | sed -n "s/^targetSdkVersion:'\([0-9]*\)'/\1/p")"
  [ "${target:-0}" -ge 36 ] && ok "targetSdk $target (Play requires 36 for new apps and updates)" || bad "targetSdk ${target:-?} < 36"
  echo "$badging" | grep -q "application-debuggable" && bad "the APK is debuggable" || ok "not debuggable"
  manifest="$("$bt/aapt2" dump xmltree --file AndroidManifest.xml "$apk" 2>/dev/null)"
  if echo "$manifest" | grep -q "usesCleartextTraffic.*=true\|usesCleartextTraffic(0x[0-9a-f]*)=(type 0x12)0xffffffff"; then
    bad "usesCleartextTraffic is on"
  fi
  nsc="$("$bt/aapt2" dump resources "$apk" 2>/dev/null | grep -A2 "xml/network_security_config" | sed -n 's/.*(file) \([^ ]*\).*/\1/p' | head -1)"
  if [ -n "$nsc" ] && "$bt/aapt2" dump xmltree --file "$nsc" "$apk" 2>/dev/null | grep -q "cleartextTrafficPermitted.*=false\|cleartextTrafficPermitted(0x[0-9a-f]*)=(type 0x12)0x0"; then
    ok "cleartext traffic refused (network security config)"
  else
    bad "cleartext traffic isn't refused: is src/main/res/xml/network_security_config.xml in the build?"
  fi
  dex="$tmp/dex"; mkdir -p "$dex"; unzip -q -o "$apk" 'classes*.dex' -d "$dex"
  if grep -a -q "ws://10.0.2.2" "$dex"/classes*.dex; then
    bad "the default server is the development address (build with -PdeeplineServerUrl=wss://<host>/battle)"
  elif grep -a -q "deepline.example/privacy" "$dex"/classes*.dex; then
    bad "the privacy policy link is the placeholder (-PdeeplinePrivacyPolicyUrl=https://...)"
  else
    ok "server and privacy policy addresses set"
  fi
else
  warn "no release APK or aapt2: build ./gradlew :app:assembleRelease for the manifest checks"
fi

echo "16 KB page sizes"
unzip -q -o "$aab" 'base/lib/*' -d "$tmp/aab" 2>/dev/null
libs="$(find "$tmp/aab" -name '*.so' \( -path '*arm64-v8a*' -o -path '*x86_64*' \) 2>/dev/null)"
if [ -z "$libs" ]; then
  ok "no 64-bit native libraries"
else
  for so in $libs; do
    name="${so#"$tmp"/aab/base/lib/}"
    aligns="$(readelf -lW "$so" | awk '$1=="LOAD"{print $NF}' | sort -u)"
    small=0
    for a in $aligns; do [ "$((a))" -lt 16384 ] && small=1; done
    if [ "$small" = 0 ]; then
      ok "$name LOAD alignment $(echo $aligns)"
    else
      bad "$name LOAD alignment $(echo $aligns) (needs 0x4000)"
    fi
  done
fi
if [ -n "$apk" ] && [ -x "$bt/zipalign" ]; then
  "$bt/zipalign" -c -P 16 4 "$apk" >/dev/null 2>&1 && ok "APK zip-aligned for 16 KB pages" || bad "zipalign -c -P 16 failed"
fi

echo "Signing and mapping"
if jarsigner -verify "$aab" >/dev/null 2>&1 && jarsigner -verify "$aab" 2>&1 | grep -q "jar verified"; then
  ok "bundle signed: $(keytool -printcert -jarfile "$aab" 2>/dev/null | sed -n 's/^Owner: //p' | head -1)"
else
  bad "bundle not signed: set the upload key (docs/release-checklist.md, step 4)"
fi
entries="$(unzip -l "$aab")"
echo "$entries" | grep -q "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map" \
  && ok "R8 mapping file in the bundle (Play deobfuscates crashes with it)" \
  || warn "no R8 mapping file in the bundle (is R8 off?)"

[ "$fail" = 0 ] && echo "All checks passed." || echo "Some checks failed."
exit "$fail"
