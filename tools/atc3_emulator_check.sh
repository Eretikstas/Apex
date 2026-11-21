#!/bin/bash
# Runs on the emulator (see autoisf-atc3-ci.yml). Never stops at the first failure: it writes what
# happened to emu-out/summary.txt and keeps going.
set +e
OUT=emu-out; mkdir -p $OUT/shots
PKG=info.nightscout.androidaps
APP=$(grep -E "full/debug/.*\.apk$" apks.txt | grep -v androidTest | head -1)
TEST=$(grep -E "androidTest/full/debug/.*\.apk$" apks.txt | head -1)
S=$OUT/summary.txt
echo "app: $APP" > $S; echo "test app: ${TEST:-none}" >> $S

shot() { adb exec-out screencap -p > "$OUT/shots/$1.png"; }

# Only the real screenshots (input screens): the app in Russian, our test drives it and takes the pictures.
if [ "$SCREENS_ONLY" = "1" ] && [ -n "$TEST" ]; then
  adb install -r -g "$APP" >> $S 2>&1
  adb install -r -g "$TEST" >> $S 2>&1
  adb shell cmd locale set-app-locales $PKG --locales ru-RU >> $S 2>&1
  TPKG=$(adb shell pm list instrumentation | tr -d '\r' | grep "target=$PKG" | head -1 | sed -E 's#instrumentation:([^ ]+).*#\1#')
  adb logcat -c
  timeout 1800 adb shell am instrument -w -e class app.aaps.e2e.RealScreensTest $TPKG > $OUT/test_RealScreensTest.txt 2>&1
  grep -E "^OK \(|^FAILURES|Tests run:|INSTRUMENTATION" $OUT/test_RealScreensTest.txt >> $S
  adb logcat -d > $OUT/logcat_RealScreensTest.txt
  mkdir -p $OUT/realshots
  adb pull /sdcard/Android/data/$PKG/files/realshots/. $OUT/realshots/ >> $S 2>&1
  ls $OUT/realshots >> $S
  cat $S
  exit 0
fi

adb logcat -c
adb install -r -g "$APP" >> $S 2>&1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null

# 1. Fresh start: what the user sees first.
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 > /dev/null
sleep 20; shot 01_first_start
sleep 20; shot 02_after_40s

# 2. Random taps on a fresh install, in rounds, with a screenshot after each round.
for r in 1 2 3 4 5 6; do
  adb shell monkey -p $PKG --throttle 150 --pct-syskeys 0 --pct-appswitch 5 --ignore-crashes --ignore-timeouts \
    --ignore-security-exceptions -s $r -v 800 > $OUT/monkey_$r.txt 2>&1
  shot "03_monkey_round_$r"
  adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 > /dev/null
  sleep 3
done
adb logcat -d > $OUT/logcat_monkey.txt
echo "== random taps" >> $S
echo "crashes (FATAL EXCEPTION in the app): $(grep -c "FATAL EXCEPTION" $OUT/logcat_monkey.txt)" >> $S
echo "not responding (ANR): $(grep -c "ANR in $PKG" $OUT/logcat_monkey.txt)" >> $S
grep -A6 "FATAL EXCEPTION" $OUT/logcat_monkey.txt | head -120 > $OUT/crashes.txt
grep -h "CRASH\|NOT RESPONDING" $OUT/monkey_*.txt | sort | uniq -c >> $S

# 3. The official end to end tests of this base, one class at a time.
if [ -n "$TEST" ]; then
  adb install -r -g "$TEST" >> $S 2>&1
  TPKG=$(adb shell pm list instrumentation | tr -d '\r' | grep "target=$PKG" | head -1 | sed -E 's#instrumentation:([^ ]+).*#\1#')
  echo "== end to end tests ($TPKG)" >> $S
  # The set-up wizard test wants a fresh install: the random taps above have already walked through it.
  adb uninstall $PKG >> $S 2>&1
  adb install -r -g "$APP" >> $S 2>&1
  adb install -r -g "$TEST" >> $S 2>&1
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
  for C in app.aaps.e2e.SetupWizardE2ETest app.aaps.e2e.Atc3EmulatorScenarioTest app.aaps.e2e.ErrorActivityInjectionTest \
           app.aaps.LoopTest app.aaps.RunningConfigurationTest app.aaps.CobExtendedCarbsTest app.aaps.RunningModeReconcilerIntegrationTest; do
    # Each class on a fresh install: our long ATC3 scenarios leave a configured ATC3 pump and a running mode behind,
    # which the following blaqone tests are not written for.
    adb uninstall $PKG > /dev/null 2>&1
    adb install -r -g "$APP" > /dev/null 2>&1
    adb install -r -g "$TEST" > /dev/null 2>&1
    adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
    adb logcat -c
    # Our ATC3 scenarios take about 2 min each (30+ of them); the other classes are short.
    T=1200; [ "$C" = app.aaps.e2e.Atc3EmulatorScenarioTest ] && T=6000
    timeout $T adb shell am instrument -w -e class $C $TPKG > $OUT/test_$C.txt 2>&1
    res=$(grep -E "^OK \(|^FAILURES|Tests run:|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_RESULT: shortMsg" $OUT/test_$C.txt | tr '\n' ' ')
    echo "$C: ${res:-no result (timeout?)}" >> $S
    adb logcat -d > $OUT/logcat_$C.txt
    shot "04_after_${C##*.}"
  done
fi
cat $S
exit 0
