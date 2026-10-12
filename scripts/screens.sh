#!/usr/bin/env bash
# Launches Lumen on the CI emulator in every design/mode on cover + inner screen sizes and screenshots it.
out=screens; mkdir -p $out
A="-n com.lumen.player/.MainActivity"
shot() { sleep ${2:-4}; adb exec-out screencap -p > "$out/$1.png"; echo "shot $1"; }
size() { adb shell wm size "$1"; adb shell wm density "$2"; sleep 3; }
go() { adb shell am start $A "$@" >/dev/null 2>&1; }

adb shell pm grant com.lumen.player android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell settings put global window_animation_scale 0.5
size 1080x2520 420   # Fold7 cover screen ≈ 411dp
go --es lumen.design AURORA --es lumen.theme DARK; shot 00-first-launch 8
go --ez lumen.demo true --es lumen.design AURORA --es lumen.theme DARK; sleep 8
for d in AURORA OBSIDIAN FLUID; do
  for t in DARK LIGHT; do
    go --es lumen.design $d --es lumen.theme $t --es lumen.route home; shot "cover-$d-$t-1-home" 5
    go --es open player; shot "cover-$d-$t-2-player" 4
    go --es lumen.route library; shot "cover-$d-$t-3-library" 4
  done
done
go --es lumen.design AURORA --es lumen.theme DARK
for r in "folder?path=Playlists%2FEmotional" search import downloads settings playlists nas; do
  n=$(echo "$r" | cut -d'?' -f1); go --es lumen.route "$r"; shot "cover-AURORA-DARK-4-$n" 4
done
size 1968x2184 420   # Fold7 inner screen ≈ 750dp
for d in AURORA OBSIDIAN FLUID; do
  go --es lumen.design $d --es lumen.theme DARK --es lumen.route home; shot "inner-$d-DARK-1-home" 5
  go --es open player; shot "inner-$d-DARK-2-player" 4
  go --es lumen.route "folder?path=Playlists%2FHip%20Hop"; shot "inner-$d-DARK-3-folder" 4
done
go --es lumen.design FLUID --es lumen.theme LIGHT --es lumen.route settings; shot "inner-FLUID-LIGHT-4-settings" 4
adb logcat -d -b crash > $out/crash.txt
adb logcat -d | grep -iE "AndroidRuntime|FATAL|lumen" | tail -200 > $out/log.txt
ls $out
