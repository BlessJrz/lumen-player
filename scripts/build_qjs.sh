#!/usr/bin/env bash
# Builds the QuickJS-NG command-line engine for Android arm64 and ships it as libqjs.so, so Android extracts it
# with execute permission. yt-dlp uses it (--js-runtimes quickjs:…) to solve YouTube's JavaScript challenges.
set -euo pipefail
QJS_TAG="${QJS_TAG:-v0.17.0}"
NDK="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"
[ -d "$NDK" ] || { echo "Android NDK not found"; exit 1; }
OUT=app/src/main/jniLibs/arm64-v8a
mkdir -p "$OUT"
WORK=$(mktemp -d)
git clone --depth 1 --branch "$QJS_TAG" https://github.com/quickjs-ng/quickjs.git "$WORK/src"
cmake -S "$WORK/src" -B "$WORK/build" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-31 -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=OFF -DQJS_BUILD_EXAMPLES=OFF
cmake --build "$WORK/build" --target qjs_exe -j"$(nproc)" || cmake --build "$WORK/build" -j"$(nproc)"
BIN=$(find "$WORK/build" -maxdepth 2 -type f -name qjs | head -1)
[ -n "$BIN" ] || { echo "qjs binary not produced"; exit 1; }
"$NDK"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip "$BIN" || true
cp "$BIN" "$OUT/libqjs.so"
file "$OUT/libqjs.so" || true
ls -la "$OUT"
