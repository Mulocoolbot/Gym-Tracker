# <img src="docs/assets/ic_gymtracker_mark.svg" width="36" height="36" align="top" alt="MFit" /> MFit

## Overview

MFit is an offline-first workout tracker for Android and iOS written in Kotlin with Jetpack Compose and Compose Multiplatform (Material 3). It provides set logging, custom weekly split scheduling, background rest timers, and local strength progression analytics with zero network dependency required for daily use.

## Core Features

- **Schedule-Aware Streak Tracking**: Evaluates workout consistency against the user's 7-day weekly split. Scheduled rest days are skipped automatically, and streaks reset only after missing two consecutive scheduled workout days.
- **Epley e1RM Progression Analytics**: Computes estimated one-rep max (`weight * (1 + reps / 30)`) per set and plots historical strength curves with percentage gain/loss badges.
- **2D Anatomical Muscle Heatmap**: Maps logged exercises to primary and secondary muscle groups across front and back body silhouettes rendered via Compose `Canvas`.
- **Background Rest Timer with Audio Ducking**: Runs countdown timers with local completion alerts (`60s` warmup, `120s` normal set, `180s` final set) using Android Foreground Service / `AlarmManager` (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) and iOS `UNUserNotificationCenter` / `AVAudioSession` (`DuckOthers` + `NotifyOthersOnDeactivation`), lowering background music briefly and restoring volume after playback or dismissal.
- **Multi-Provider Account Linking**: Links Google and Email credentials onto a single persistent user ID (`uid`) via DataStore Preferences so local Room records remain unified across providers. On iOS (or builds without Firebase config), offline-first mode and email OTP verification work locally without requiring Firebase.
- **New-User Interactive Tutorial**: Guides newly registered accounts once through a 5-step (`1/5`–`5/5`) spotlight walkthrough covering Streak, Today's Plan, Weekly Plan, Workout logging, and Stats. Returning logins and offline-only users skip the tutorial automatically, and completion state persists in DataStore.
- **Offline JSON Backup & Restore**: Exports and imports the full local database (`user_profile`, `workout_plan`, `exercise_history`, `workout_session`, and `workout_set`) as a versioned JSON document via Android Storage Access Framework or iOS Share Sheet / Files (`UIDocumentPickerViewController`).

## Architecture & Tech Stack

- **Modules**: Shared KMP library (`composeApp`: `commonMain`, `androidMain`, `iosMain`, `commonTest`), Android app entry (`app`), and iOS host app (`iosApp`)
- **UI**: Compose Multiplatform (`1.8.2`), Material 3, Navigation Compose, custom `Canvas` charts and vector assets
- **Persistence**: Room KMP (`2.7.2` with KSP + bundled SQLite) and DataStore Preferences (`1.1.7`)
- **Concurrency**: Kotlin Coroutines (`1.10.2`) and reactive `StateFlow` / `Flow` pipelines
- **Android**: `minSdk = 26`, `targetSdk = 34`, `compileSdk = 35` (JDK 17), Foreground Service (`specialUse`), `AlarmManager`, `AudioFocusRequest`
- **iOS**: Deployment target `15.0+` (`iosArm64`, `iosSimulatorArm64`), `UIKit` haptics & edge swipe-back, `AVFAudio`, `UserNotifications`

## Build & Test

Run all Android and shared KMP unit tests:

```bash
./gradlew :app:testDebugUnitTest :composeApp:testDebugUnitTest
```

Assemble the Android debug APK:

```bash
./gradlew :app:assembleDebug
```

Build the iOS Kotlin framework and Xcode simulator app (macOS with Xcode):

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

> Firebase (`app/google-services.json` or `iosApp/iosApp/GoogleService-Info.plist`) is optional; without it, MFit runs in offline-first mode with local persistence and JSON backup/restore.

