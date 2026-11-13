#!/usr/bin/env bash
# Runs inside the emulator step of .github/workflows/atc3-screens.yml.
# Sets the emulator to Russian, installs the app and its test APK, runs Atc3RuScreensTest (or the
# class in SCREENS_TEST, e.g. app.aaps.e2e.RingSkinScreensTest) and pulls
# the screenshots to out/screens.
set -x
mkdir -p out/screens
adb wait-for-device

boot_done() { [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; }
locale_now() { adb shell getprop persist.sys.locale | tr -d '\r'; }

if [ "$(locale_now)" != "ru-RU" ]; then
  adb root
  sleep 5
  adb wait-for-device
  adb shell "setprop persist.sys.locale ru-RU; setprop ctl.restart zygote"
  sleep 15
  adb wait-for-device
  for i in $(seq 1 90); do boot_done && break; sleep 2; done
  sleep 10
fi
echo "Locale: $(locale_now)"

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

APK=$(ls app/build/outputs/apk/full/debug/*.apk | head -1)
TEST_APK=$(ls app/build/outputs/apk/androidTest/full/debug/*.apk | head -1)
adb install -r -g "$APK"
adb install -r -g "$TEST_APK"

adb logcat -c
adb shell am instrument -w -r -e class "${SCREENS_TEST:-app.aaps.e2e.Atc3RuScreensTest}" \
  info.nightscout.androidaps.test/app.aaps.runners.HiltTestRunner | tee out/instrument.txt

adb pull /sdcard/Android/data/info.nightscout.androidaps/files/screens/. out/screens/ || true
adb logcat -d > out/logcat.txt || true
ls out/screens | head -400
COUNT=$(ls out/screens/*.png 2>/dev/null | wc -l)
echo "Screenshots: $COUNT"
# Fail only when nothing came out, so a partial walk still gives its screenshots.
[ "$COUNT" -gt 0 ]
