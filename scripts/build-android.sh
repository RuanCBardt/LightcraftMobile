#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK folder}"
case "$(uname -s)" in Darwin) host=darwin-x86_64;; Linux) host=linux-x86_64;; *) echo 'Use build-android.ps1 on Windows'; exit 1;; esac
llvm="$ANDROID_HOME/ndk/28.2.13676358/toolchains/llvm/prebuilt/$host/bin"
for pair in "arm64-v8a:aarch64-linux-android" "x86_64:x86_64-linux-android"; do
  abi="${pair%%:*}"; target="${pair##*:}"
  rustup target add "$target"
  key="CARGO_TARGET_$(echo "$target" | tr '[:lower:]-' '[:upper:]_')_LINKER"
  cc="CC_$(echo "$target" | tr '-' '_')"
  env "$key=$llvm/${target}29-clang" "$cc=$llvm/${target}29-clang" cargo build --locked --release -p lightcraft-mobile --target "$target"
  mkdir -p "apps/android/app/src/main/jniLibs/$abi"
  cp "target/$target/release/liblightcraft_mobile.so" "apps/android/app/src/main/jniLibs/$abi/"
done
cd apps/android
./gradlew assembleDebug
