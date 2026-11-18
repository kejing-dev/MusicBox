#!/usr/bin/env bash
# Music Box dev harness (macOS). Pinned to one emulator: DEV (borrowed with the factory's emu_borrow.py).
#   tools/mb.sh cmd NAME [ARG]     debug broadcast:  am broadcast -a io.embodit.musicbox.DEBUG --es cmd NAME --es a ARG
#   tools/mb.sh shot OUT.png       still capture (adb exec-out screencap, 3 tries; pico-cli's 10 s timeout is too short here)
#   tools/mb.sh log [REGEX]        logcat lines of tag MusicBox (optionally filtered)
#   tools/mb.sh install            install the debug APK and start the app
set -u
DEV="${DEV:-emulator-5562}"
PKG=io.embodit.musicbox
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
HERE="$(cd "$(dirname "$0")" && pwd)"
case "${1:-}" in
  cmd) "$ADB" -s "$DEV" shell am broadcast -a $PKG.DEBUG -p $PKG --es cmd "$2" --es a "\"${3:-}\"" >/dev/null ;;
  shot) for i in 1 2 3; do "$ADB" -s "$DEV" exec-out screencap -p > "$2.tmp" 2>/dev/null && [ "$(wc -c < "$2.tmp")" -gt 100000 ] && mv "$2.tmp" "$2" && break; sleep 2; done; rm -f "$2.tmp" ;;
  log) "$ADB" -s "$DEV" logcat -d -s MusicBox:V | grep -E "${2:-.}" ;;
  install) "$ADB" -s "$DEV" install -r -d "$HERE/../app/build/outputs/apk/debug/app-debug.apk" && "$ADB" -s "$DEV" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 ;;
  *) sed -n 2,6p "$0" ;;
esac
