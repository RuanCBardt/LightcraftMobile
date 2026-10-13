# Android validation — 2026-10-13

Validated locally on Windows using Rust 1.99 (gnullvm host + LLVM-MinGW), JDK 21 and Android SDK 36.
The original repository revision used is `b3d392b4994ee99dc389890f006600769d0c4ff5`.

| Check | Result |
|---|---|
| `cargo check -p lightcraft-mobile` | Passed |
| `cargo fmt -p lightcraft-mobile --check` | Passed |
| `cargo clippy -p lightcraft-mobile -- -D warnings` | Passed |
| `cargo test -p lightcraft-mobile` | 2 tests passed |
| Native release builds: arm64-v8a and x86_64 | Passed |
| ARM64 ELF segment alignment | All LOAD segments aligned to 0x4000 (16 KiB) |
| JNI library dependencies | Only Android system libc, libm and libdl |
| Android Kotlin + instrumentation compilation | Passed |
| `assembleDebug` | Passed; development-signed APK |
| `lintDebug` | Passed, no errors; pinned-version suggestions and dynamic ABI-analysis warning remain |
| `connectedDebugAndroidTest` on API 36 x86_64 emulator | 2 tests passed |
| Compose editor screenshot | Captured and visually inspected |

Rust tests cover idempotent preset intensity, manual-adjustment precedence and invalid values/crop.
Android native tests exercise actual JNI, JPEG decoding/rendering, synthetic embedded-XMP DNG preset
import, unsupported-profile reporting, 0% intensity, manual values returned by the engine, crop,
rotation, preset persistence and missing-file errors. The Compose flow tests import a generated
image, choose a preset, open adjustments, crop, undo and save to the Android gallery.

The DNG-preset test uses a synthetic packet, not a user's real preset pack. This does not establish
visual parity with Lightroom. Physical ARM64 device performance, large RAW memory behavior,
HEIC, the full upstream desktop CI and Play Store distribution were not validated.
No shared upstream image-processing code was changed.
