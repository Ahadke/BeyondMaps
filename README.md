# BeyondMaps

BeyondMaps is an Android travel assistant focused on **offline-first help for travelers**.  
It combines on-device LLM inference (LiteRT-LM), optional Qualcomm NPU acceleration, OCR/vision flows, and vector retrieval over a local Florence knowledge pack.

## What It Does

- **Travel Guide chat**: Ask travel questions and get local-context answers.
- **Translator**: Translate text (and optionally speak output) using an on-device model.
- **Image-assisted flows**:
  - Translate text from captured/uploaded photos.
  - Identify/describe visual scenes with a vision-capable model.
- **Offline vector context (RAG)**: Uses a local vector pack for Florence-specific retrieval.

## Tech Stack

- Kotlin + Jetpack Compose
- Android Gradle Plugin `8.13.1`
- Kotlin `2.2.0`
- compileSdk/targetSdk `36`, minSdk `24`
- LiteRT-LM (`com.google.ai.edge.litertlm`)
- ML Kit text recognition
- arm64 native libraries for Qualcomm runtime integration

## Prerequisites

- Android Studio (latest stable recommended)
- Android SDK for API 36
- A physical Android device (arm64 recommended for NPU path)
- `adb` available in your terminal
- Large local model files:
  - Travel model: `model.litertlm`
  - Vision model (any one of):
    - `FastVLM-0.5B.qualcomm.sm8750.litertlm`
    - `FastVLM-0.5B.sm8850.litertlm`
    - `FastVLM-0.5B.litertlm`
    - `fastvlm.litertlm`
- Vector pack: `florence_pack.json`

## Project Structure (High-Level)

- `app/` - Android app source
- `app/src/main/java/com/beyondmaps/ai/` - model loading + LiteRT runtime
- `app/src/main/java/com/beyondmaps/rag/vector/` - vector retrieval pipeline
- `app/src/main/java/com/beyondmaps/ui/` - Compose UI screens/components
- `data-rag/florence_pack.json` - base Florence vector pack

## Setup: Local Model + Data Files

The app loads required model/data files from app-specific external storage:

`/sdcard/Android/data/com.beyondmaps/files/`

### 1) Install and launch app once

Run from project root:

```bash
./gradlew :app:installDebug
adb shell am start -n com.beyondmaps/.MainActivity
```

Launching once ensures the app-specific folder is created on device.

### 2) Push model and vector files

Replace placeholder local paths with your actual files:

```bash
adb push /absolute/path/to/model.litertlm /sdcard/Android/data/com.beyondmaps/files/model.litertlm
adb push /absolute/path/to/FastVLM-0.5B.qualcomm.sm8750.litertlm /sdcard/Android/data/com.beyondmaps/files/FastVLM-0.5B.qualcomm.sm8750.litertlm
adb push /absolute/path/to/florence_pack.json /sdcard/Android/data/com.beyondmaps/files/florence_pack.json
```

If you do not use the first FastVLM filename, make sure the destination filename matches one of the accepted names listed above.

## Run the App

### Option A: Android Studio (recommended)

1. Open this folder in Android Studio.
2. Wait for Gradle sync to finish.
3. Select a connected device (arm64 physical device preferred).
4. Run the `app` configuration.

### Option B: Command line

```bash
./gradlew :app:installDebug
adb shell am start -n com.beyondmaps/.MainActivity
```

## First-Run Validation Checklist

- App opens to home screen with **Travel Guide** and **Translator** cards.
- Enter Travel Guide and send a text prompt.
- Translator screen:
  - type text and translate,
  - optionally test mic input and speaker output.
- Image flow in chat:
  - choose **Translate Text** on an image,
  - choose **Identify** on an image (requires FastVLM model present).

## Troubleshooting

- **"Model not ready" / missing `model.litertlm`**
  - Verify file exists at:
    - `/sdcard/Android/data/com.beyondmaps/files/model.litertlm`
  - Relaunch app after copying.

- **FastVLM missing error in image identify flow**
  - Ensure one valid FastVLM filename exists under:
    - `/sdcard/Android/data/com.beyondmaps/files/`

- **No local travel context appears**
  - Verify `florence_pack.json` exists in app files directory.
  - Confirm the file is non-empty and valid JSON.

- **NPU path fails**
  - Runtime falls back through `NPU -> GPU -> CPU`.
  - Confirm device/SoC compatibility and native libs availability.

- **Build errors on old toolchains**
  - Update Android Studio + SDK/Build tools and resync Gradle.

## Development Notes

- This project currently targets arm64 (`abiFilters += "arm64-v8a"`).
- `jniLibs` are packaged with legacy packaging enabled.
- The app requests camera/media/mic permissions for multimodal features.

## Useful Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Install debug build
./gradlew :app:installDebug

# Run lint
./gradlew :app:lintDebug
```

## Download / Test the App

To test BeyondMaps, download `beyondmaps.apk` from the GitHub release once available.

## Team

- Prasannadatta Kawadkar — ppkawadkar@ucdavis.edu
- Aayusha Hadke — aayusha2611@gmail.com
- Celine John Philip — celinejp.03@gmail.com
- Bismanpal Singh — bismanpal@gmail.com

## License

Add your license details here (e.g., MIT, Apache-2.0, or proprietary internal use).
