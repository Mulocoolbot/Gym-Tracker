# <img src="docs/assets/ic_gymtracker_mark.svg" width="36" height="36" align="top" alt="MFit" /> MFit

## Overview

MFit is an offline-first Android workout tracker written in Kotlin with Jetpack Compose (Material 3). It provides fast set logging, custom weekly split scheduling, background rest timers, and local strength progression analytics with zero network dependency required for daily use.

## Core Features

- **Schedule-Aware Streak Tracking**: Evaluates workout consistency against the user's 7-day weekly split. Scheduled rest days are skipped automatically, and streaks reset only after missing two consecutive scheduled workout days.
- **Epley e1RM Progression Analytics**: Computes estimated one-rep max (`weight * (1 + reps / 30)`) per set and plots historical strength curves with percentage gain/loss badges.
- **2D Anatomical Muscle Heatmap**: Maps logged exercises to primary and secondary muscle groups across front and back body silhouettes rendered via Compose `Canvas`.
- **Background Rest Timer with Audio Ducking**: Runs a foreground countdown service with `AlarmManager` exact-alarm fallback (`60s` warmup, `120s` normal set, `180s` final set) and requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` so background music ducks briefly during alerts and restores volume on dismissal.
- **Multi-Provider Account Linking**: Links Google and Email credentials onto a single persistent user ID (`uid`) via DataStore Preferences so local Room records remain unified across providers.
- **Offline JSON Backup & Restore**: Exports and imports the full local database (`user_profile`, `workout_plan`, `exercise_history`, `workout_session`, and `workout_set`) as a versioned JSON document via the Android Storage Access Framework.

## Architecture & Tech Stack

- **Architecture**: Single-Activity MVVM (`data`, `timer`, `util`, `ui`) under package `com.mulocoolbot.gymtracker`
- **UI**: Jetpack Compose, Material 3, Navigation Compose, custom `Canvas` charts and vector icons
- **Persistence**: Room Database (`2.6.1` with KSP) and Jetpack DataStore Preferences (`1.1.1`)
- **Concurrency**: Kotlin Coroutines and reactive `StateFlow` / `Flow` pipelines
- **Background Execution**: Android Foreground Service (`specialUse`), `AlarmManager`, `BroadcastReceiver`, and `AudioFocusRequest`
- **SDK Levels**: `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34` (JDK 17)

## Build & Test

Run the JUnit 4 unit test suite:

```bash
./gradlew testDebugUnitTest
```

Assemble the debug APK:

```bash
./gradlew assembleDebug
```

