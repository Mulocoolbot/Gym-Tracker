# Rencana Migrasi iOS — MFit (Gym Tracker)

Dokumen ini disusun berdasarkan hasil audit nyata repositori pada Sprint 0 untuk memigrasikan aplikasi Android **MFit** (`com.mulocoolbot.gymtracker`) ke **Kotlin Multiplatform (KMP) + Compose Multiplatform** tanpa mengubah desain UI, skema data, maupun alur CI Android yang sudah berjalan.

---

## 0. Ringkasan Audit Sprint 0

| Aspek | Kondisi Saat Ini (Hasil Audit Nyata) | Tindak Lanjut Migrasi |
| :--- | :--- | :--- |
| **Build & CI** | Gradle `8.8`, AGP `8.5.2`, Kotlin `2.0.0`, KSP `2.0.0-1.0.24`, `compileSdk = 34`, `minSdk = 26`, JDK 17. CI di `.github/workflows/android.yml` menjalankan `./gradlew testDebugUnitTest` dan `./gradlew assembleDebug assembleRelease` lalu mengarsipkan APK dari `app/build/outputs/apk/`. | Upgrade ke toolchain KMP/K2 (`Gradle 8.11.1`, `AGP 8.9.1`, `Kotlin 2.1.21`, `CMP 1.8.2`, `KSP 2.1.21-2.0.2`), pertahankan `:app` sebagai wrapper agar `.github/workflows/android.yml` tidak berubah. |
| **Database & Prefs** | Room `2.6.1` (`Entities.kt`, `GymDao.kt`, `GymDatabase.kt` dengan 5 entitas: `UserProfileEntity`, `WorkoutPlanEntity`, `ExerciseHistoryEntity`, `WorkoutSessionEntity`, `WorkoutSetEntity`) + DataStore Preferences `1.1.1` (`SessionPreferences.kt`). | Migrasi ke Room KMP `2.7.2` + `BundledSQLiteDriver` (`2.5.2`) dan `datastore-preferences-core` `1.1.7` di `commonMain`. |
| **Backup JSON** | `GymBackupManager.kt` memakai `org.json.JSONObject`/`JSONArray` + parser regex fallback (skema v1). | Ganti serializer/deserializer internal ke `kotlinx-serialization-json` `1.8.1` dengan menjaga struktur JSON v1 identik 100% agar kompatibel lintas platform. |
| **API JVM/Android di Logika** | `java.time.*`, `java.util.Locale`, `java.util.UUID`, dan `%.1f.format()` dipakai di `WorkoutCalculator.kt`, `StreakEvaluator.kt`, `HomeViewModel.kt`, `WorkoutViewModel.kt`, `StatsViewModel.kt`, `AuthOnboardingViewModel.kt`, `EmailOtpManager.kt`, `SessionPreferences.kt`, dan `OnboardingScreens.kt`. | Ganti `java.time.*` ke `kotlinx-datetime` `0.6.2`, serta ganti `String.format` / `UUID` ke helper Kotlin murni di `commonMain`. |
| **Grafik & Canvas** | `StatsScreen.kt` memakai `android.graphics.Paint`, `android.graphics.Typeface`, dan `drawContext.canvas.nativeCanvas.drawText` untuk label grafik e1RM. | Ganti ke Compose `TextMeasurer` + `drawText` agar berjalan identik di Skia (iOS) dan Android. |
| **Timer, Audio & Haptik** | `RestTimerController.kt` (state murni) terpisah dari `RestTimerService.kt` (`ForegroundService` + `AlarmManager`), `GymAlarmPlayer.kt` (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, `MediaPlayer`, `Vibrator`), dan `OpeningSoundEffects.kt` (`AudioTrack` PCM + `Vibrator`). | Pertahankan `RestTimerState` & hitung mundur di `commonMain`; bungkus eksekusi latar, notifikasi, audio ducking, dan haptik lewat kontrak `RestTimerController` / bridge platform. |
| **File Picker** | `SettingsScreen.kt` memakai `ActivityResultContracts.CreateDocument`, `OpenDocument`, dan `Context.contentResolver`. | Ekstrak ke kontrak `JsonBackupController` (`SAF` di Android vs Share Sheet / `UIDocumentPickerViewController` di iOS). |
| **Auth & Email OTP** | `GoogleAuthLauncher.kt` (`GoogleSignIn` + `AccountPicker`), `SessionPreferences.kt` (`FirebaseAuth`), `EmailOtpSender.kt` (`javax.mail` + `BuildConfig.SMTP_*` + `NotificationCompat`). | Bungkus lewat kontrak `AuthBridge` offline-first agar build iOS tetap sukses tanpa `GoogleService-Info.plist`. |
| **Permission & Context** | `SystemPermissionsGate.kt`, `MainScaffold.kt`, `WorkoutScreen.kt`, dan `MFitOpeningIntro.kt` memanggil `LocalContext.current` dan `POST_NOTIFICATIONS` / exact alarm / battery optimization. | Pindahkan permintaan izin sistem ke implementasi platform melalui kontrak `RestTimerController` / `AuthBridge`. |
| **Siap `commonMain`** | `Color.kt`, `Type.kt`, `Theme.kt`, `AppIcons.kt` (10 `ImageVector`), `BottomNavTab.kt`, `RestTimerState.kt`, dan alur bisnis utama `GymRepository.kt`. | Pindah langsung ke `composeApp/src/commonMain` dengan penyesuaian minimal. |

---

## 1. Struktur Modul Target

Opsi paling sederhana adalah **mempertahankan modul `:app` sebagai Android wrapper tipis** dan menambahkan modul shared **`:composeApp`** (KMP + Compose Multiplatform) serta proyek Xcode **`iosApp/`**.

Alasan memilih struktur ini:
- `.github/workflows/android.yml` tidak perlu dimodifikasi. Perintah `./gradlew testDebugUnitTest`, `./gradlew assembleDebug assembleRelease`, `archivesName = "MFit"`, serta path artefak `app/build/outputs/apk/release/*-release.apk` dan `app/build/outputs/apk/debug/*-debug.apk` tetap berjalan di modul `:app`.
- Konfigurasi signing release, `BuildConfig` SMTP, dan `google-services.json` tetap berada di `:app`.

```text
Gym-Tracker/
├── build.gradle.kts
├── settings.gradle.kts                  # include(":app", ":composeApp")
├── gradle/
│   ├── libs.versions.toml               # Terpusat untuk :app dan :composeApp
│   └── wrapper/gradle-wrapper.properties
├── app/                                 # Android wrapper tipis (tetap untuk CI & APK)
│   ├── build.gradle.kts                 # implementation(project(":composeApp"))
│   ├── google-services.json
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mulocoolbot/gymtracker/
│       │   ├── MainActivity.kt          # Memanggil GymTrackerApp() dari :composeApp
│       │   └── timer/                   # RestTimerService, RestAlarmReceiver, GymAlarmPlayer
│       └── res/                         # mipmap-anydpi-v26, ic_notification_timer.xml, values/
├── composeApp/                          # Shared KMP + Compose Multiplatform
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/
│       │   ├── composeResources/drawable/ic_gymtracker_mark.xml
│       │   └── kotlin/com/mulocoolbot/gymtracker/
│       │       ├── App.kt               # Entry composable bersama (GymTrackerTheme + MainScaffold)
│       │       ├── data/                # local (Room KMP), auth (DataStore Core), backup, repository
│       │       ├── platform/            # 5 kontrak expect + tipe pendukungnya
│       │       ├── timer/               # RestTimerState & RestTimerController (logika shared)
│       │       ├── ui/                  # theme, components, intro, auth, home, plan, workout, stats, settings, navigation
│       │       └── util/                # WorkoutCalculator, StreakEvaluator (kotlinx-datetime)
│       ├── commonTest/kotlin/...        # Unit test shared (berjalan di JVM & iOS Simulator)
│       ├── androidMain/kotlin/...       # Implementasi actual Android
│       └── iosMain/kotlin/...           # Implementasi actual iOS + MainViewController.kt
└── iosApp/                              # Proyek Xcode SwiftUI minimal
    ├── iosApp.xcodeproj/
    └── iosApp/
        ├── iOSApp.swift                 # @main App struct
        ├── ContentView.swift            # UIViewControllerRepresentable -> MainViewControllerKt.MainViewController()
        ├── Info.plist                   # Permissions & background audio/notification config jika perlu
        └── Assets.xcassets/
            └── AppIcon.appiconset/      # AppIcon-1024.png + Contents.json
```

---

## 2. Daftar Versi Dependensi Kompatibel

Seluruh versi berikut kompatibel penuh dengan **Kotlin K2 Compiler**, **JDK 17** (runner Ubuntu CI), dan **KSP2** tanpa konflik versi `.klib`.

| Komponen / Library | Grup & Artefak / Plugin ID | Versi | Catatan Kompatibilitas |
| :--- | :--- | :--- | :--- |
| **Gradle Wrapper** | `gradle-8.11.1-bin.zip` | `8.11.1` | Minimum resmi AGP `8.9.1`, jalan di JDK 17. |
| **Android Gradle Plugin** | `com.android.application`, `com.android.library` | `8.9.1` | `compileSdk = 35`, `minSdk = 26`, mendukung JDK 17. |
| **Kotlin & Compose Compiler** | `org.jetbrains.kotlin.multiplatform`, `kotlin.android`, `kotlin.plugin.compose`, `kotlin.plugin.serialization` | `2.1.21` | K2 stabil, memenuhi syarat minimum Compose Multiplatform `1.8.2` (`>= 2.1.0`). |
| **Compose Multiplatform** | `org.jetbrains.compose` | `1.8.2` | Rilis stabil K2-native, mencakup `components.resources`. |
| **KSP** | `com.google.devtools.ksp` | `2.1.21-2.0.2` | Cocok eksak dengan Kotlin `2.1.21` untuk Room KMP compiler. |
| **Room KMP** | `androidx.room:room-runtime`, `androidx.room:room-compiler`, plugin `androidx.room` | `2.7.2` | Membutuhkan Kotlin `>= 2.0.0`; memuat perbaikan KSP native dan suspensi iOS background. |
| **Bundled SQLite KMP** | `androidx.sqlite:sqlite-bundled` | `2.5.2` | Driver SQLite seragam di Android dan iOS (`BundledSQLiteDriver`). |
| **DataStore Core KMP** | `androidx.datastore:datastore-preferences-core` | `1.1.7` | Mendukung `commonMain`, Android, dan iOS (`PreferenceDataStoreFactory.createWithPath`). |
| **Coroutines** | `org.jetbrains.kotlinx:kotlinx-coroutines-core`, `kotlinx-coroutines-test` | `1.10.2` | Kompatibel penuh dengan Kotlin `2.1.21` dan Room `2.7.2`. |
| **Date & Time KMP** | `org.jetbrains.kotlinx:kotlinx-datetime` | `0.6.2` | Stabil di Kotlin `2.1.21` tanpa breaking change `kotlin.time.Clock` pada `0.7.x`. |
| **JSON Serialization KMP** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.8.1` | Kompatibel dengan Kotlin `2.1.21` untuk `GymBackupManager`. |
| **Lifecycle ViewModel CMP** | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | `2.9.1` | Rilis resmi JetBrains pasangan CMP `1.8.2`. |
| **Navigation Compose CMP** | `org.jetbrains.androidx.navigation:navigation-compose` | `2.9.2` | Rilis stabil JetBrains pasangan CMP `1.8.2`. |
| **Google Services & Firebase** | `com.google.gms.google-services` (`4.4.2`), `firebase-bom` (`33.2.0`), `play-services-auth` (`21.2.0`) | Tetap | Hanya dipasang di `:app` / `androidMain`, tidak masuk `commonMain`. |

### Referensi Ringkas `gradle/libs.versions.toml`

```toml
[versions]
agp = "8.9.1"
kotlin = "2.1.21"
composeMultiplatform = "1.8.2"
ksp = "2.1.21-2.0.2"
room = "2.7.2"
sqlite = "2.5.2"
datastore = "1.1.7"
coroutines = "1.10.2"
kotlinxDatetime = "0.6.2"
kotlinxSerialization = "1.8.1"
lifecycleMultiplatform = "2.9.1"
navigationMultiplatform = "2.9.2"
googleServices = "4.4.2"
firebaseBom = "33.2.0"
playServicesAuth = "21.2.0"

[libraries]
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
androidx-datastore-preferences-core = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastore" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
jetbrains-lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycleMultiplatform" }
jetbrains-lifecycle-runtime-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycleMultiplatform" }
jetbrains-navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigationMultiplatform" }

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidxRoom = { id = "androidx.room", version.ref = "room" }
googleServices = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

---

## 3. Kontrak `expect`/`actual` Minimal (5 Kontrak)

Untuk menjaga kode tetap sederhana dan mudah dirawat, seluruh ketergantungan OS dibatasi pada 5 kontrak berikut di `composeApp/src/commonMain/kotlin/com/mulocoolbot/gymtracker/platform/`.

### 1. `PlatformDatabaseBuilder`
Menyediakan builder `GymDatabase` (Room KMP dengan `@ConstructedBy(GymDatabaseConstructor::class)` dan `BundledSQLiteDriver()`).

```kotlin
// commonMain
expect fun createGymDatabaseBuilder(): RoomDatabase.Builder<GymDatabase>

// Pemakaian bersama di commonMain
fun buildGymDatabase(): GymDatabase = createGymDatabaseBuilder()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()
```

| Platform | Implementasi `actual` |
| :--- | :--- |
| **Android** | `Room.databaseBuilder<GymDatabase>(context = appContext, name = appContext.getDatabasePath("gym_tracker.db").absolutePath)` |
| **iOS** | `Room.databaseBuilder<GymDatabase>(name = "$documentDirectory/gym_tracker.db")` memakai `NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, false, null)?.path` |

### 2. `PlatformPreferencesPath`
Menyediakan path file `.preferences_pb` untuk `PreferenceDataStoreFactory.createWithPath` di `SessionPreferences`.

```kotlin
// commonMain
expect fun getAuthPreferencesPath(): String
```

| Platform | Implementasi `actual` |
| :--- | :--- |
| **Android** | `appContext.filesDir.resolve("auth_session_prefs.preferences_pb").absolutePath` |
| **iOS** | `"$documentDirectory/auth_session_prefs.preferences_pb"` (dengan `NSDocumentDirectory`) |

### 3. `RestTimerController` (Platform Bridge)
State hitungan (`RestTimerState`, `startTimer`, `adjustTimer`, `tick`, `dismissOrSkip`, `formatMMSS`) tetap murni di `commonMain`. Interaksi OS untuk notifikasi background, audio alarm/intro, haptik, dan izin sistem dibungkus lewat satu bridge:

```kotlin
// commonMain
interface PlatformRestTimerBridge {
    fun startBackgroundTimer(durationMillis: Long, label: String)
    fun adjustBackgroundTimer(deltaSeconds: Int)
    fun stopBackgroundTimer()
    fun requestTimerPermissions()
    fun playIntroImpact(isMetallicLock: Boolean, intensity: Float = 1.0f)
}

expect fun createPlatformRestTimerBridge(): PlatformRestTimerBridge
```

| Platform | Implementasi `actual` |
| :--- | :--- |
| **Android** | Memanggil `RestTimerService.startRestTimer`, `adjustRestTimer`, `stopRestTimer` (ForegroundService + `AlarmManager` + `GymAlarmPlayer` dengan `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), launcher `POST_NOTIFICATIONS` / exact alarm / battery optimization, dan `OpeningSoundEffects` (`AudioTrack` + `Vibrator`). |
| **iOS** | Menjadwalkan `UNTimeIntervalNotificationTrigger` pada `UNUserNotificationCenter` (beserta izin `requestAuthorizationWithOptions`); saat alarm berbunyi di foreground mengaktifkan `AVAudioSession` kategori `AVAudioSessionCategoryPlayback` dengan opsi `AVAudioSessionCategoryOptionDuckOthers` + `AVAudioPlayer`, dan memicu `UIImpactFeedbackGenerator` + audio pendek untuk intro. |

### 4. `JsonBackupController`
Menangani ekspor dan impor file JSON backup dari `SettingsScreen` tanpa `ActivityResultContracts` atau `ContentResolver` di `commonMain`.

```kotlin
// commonMain
interface JsonBackupController {
    fun exportJson(fileName: String, jsonContent: String)
    fun launchImport()
}

@Composable
expect fun rememberJsonBackupController(
    onImportJson: (String) -> Unit
): JsonBackupController
```

| Platform | Implementasi `actual` |
| :--- | :--- |
| **Android** | `ActivityResultContracts.CreateDocument("application/json")` dan `ActivityResultContracts.OpenDocument()` yang membaca/menulis UTF-8 via `context.contentResolver`. |
| **iOS** | Ekspor menulis `mfit_backup.json` ke `NSTemporaryDirectory()` lalu menampilkan `UIActivityViewController` (Share Sheet) atau `UIDocumentPickerViewController(forExportingURLs:)`; impor membuka `UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON))` dan membaca string UTF-8 lewat `NSString.stringWithContentsOfURL`. |

### 5. `AuthBridge` (Offline-First / Opsional)
Memastikan alur Welcome → Sign In → Profile Setup berjalan penuh di iOS meskipun `GoogleService-Info.plist` belum dipasang, sambil tetap mempertahankan Firebase Auth + Google Sign-In + SMTP di Android.

```kotlin
// commonMain
interface AuthBridge {
    val isGoogleSignInSupported: Boolean
    suspend fun syncEmailCredentials(email: String, password: String, isRegister: Boolean)
    suspend fun deliverOtp(email: String, otpCode: String)
}

expect fun createAuthBridge(): AuthBridge

@Composable
expect fun rememberGoogleSignInAction(
    onSuccess: (email: String, displayName: String?) -> Unit,
    onErrorOrCancel: () -> Unit
): () -> Unit
```

| Platform | Implementasi `actual` |
| :--- | :--- |
| **Android** | `isGoogleSignInSupported = true`; `syncEmailCredentials` memanggil `FirebaseAuth`; `deliverOtp` memanggil `EmailOtpSender` (SMTP + notifikasi lokal); `rememberGoogleSignInAction` membungkus `GoogleAuthLauncher`. |
| **iOS** | Default offline-first (`isGoogleSignInSupported = false` selama `GoogleService-Info.plist` tidak ada); `syncEmailCredentials` no-op aman (sesi disimpan lokal di DataStore/Room); `deliverOtp` menampilkan kode OTP via notifikasi lokal `UNUserNotificationCenter`. Jika Google Sign-In SDK + `GoogleService-Info.plist` ditambahkan nanti, hanya file `actual` iOS yang diperbarui. |

---

## 4. Strategi Pemeliharaan Aset & Tema

### A. Migrasi Aset SVG (`assets/svg/`) dan Ikon

| File Sumber | Ukuran & Warna Asli | Target Android | Target Shared / iOS |
| :--- | :--- | :--- | :--- |
| `assets/svg/ic_gymtracker_mark.svg` | `64x64`, `#D4FF00` & `#F5F7FA` | Dipakai dari `composeResources` bersama. | Konversi 1:1 ke Vector Drawable `composeApp/src/commonMain/composeResources/drawable/ic_gymtracker_mark.xml` (diakses via `painterResource(Res.drawable.ic_gymtracker_mark)`). |
| `assets/svg/ic_launcher_foreground.svg` | `108x108`, `#D4FF00` & `#F5F7FA` | Tetap di `app/src/main/res/drawable/ic_launcher_foreground.xml` + `mipmap-anydpi-v26/ic_launcher.xml` berlatar `#0B0C0E`. | Render komposisi foreground di atas latar solid `#0B0C0E` menjadi `1024x1024` PNG tanpa alpha (`AppIcon-1024.png`) pada `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` beserta `Contents.json` (Xcode universal single-size). |
| `assets/svg/ic_notification_timer.svg` | `24x24`, stroke `#FFFFFF` `2px` | Tetap di `app/src/main/res/drawable/ic_notification_timer.xml` untuk small icon notifikasi Android. | Tidak perlu salinan terpisah; iOS otomatis menampilkan AppIcon pada banner `UNUserNotificationCenter`. |
| `AppIcons.kt` (10 ikon Compose) | `24.dp`, `ImageVector.Builder` | Dipakai dari `commonMain`. | Pindahkan langsung 1:1 ke `composeApp/src/commonMain/.../ui/components/AppIcons.kt`. |

### B. Token Warna 1:1 (`Color.kt`, `Theme.kt`, dan Token Lokal)

Seluruh nilai hex dipindahkan persis 1:1 tanpa perubahan kontras:

| Token | Hex | Peran di `DarkColorScheme` (`Theme.kt`) |
| :--- | :--- | :--- |
| `AppBackground` | `0xFF0B0C0E` | `background`, juga warna latar `ic_launcher_background` |
| `CardSurface` | `0xFF13151B` | `surface`, `primaryContainer` |
| `InputSurface` | `0xFF1A1D25` | `surfaceVariant`, `secondaryContainer` |
| `CardBorder` | `0xFF222530` | `outline` |
| `VoltAccent` | `0xFFD4FF00` | `primary`, `secondary`, `onPrimaryContainer` |
| `OnVolt` | `0xFF0B0C0E` | `onPrimary`, `onSecondary`, `onTertiary` |
| `TextPrimary` | `0xFFF5F7FA` | `onBackground`, `onSurface`, `onSecondaryContainer`, `onError` |
| `TextMuted` | `0xFF8E95A5` | `onSurfaceVariant` |
| `DangerRed` | `0xFFEF4444` | `error` |
| `SuccessGreen` | `0xFF10B981` | `tertiary` |

*Catatan audit:* `SettingsScreen.kt` dan `StatsScreen.kt` mendefinisikan beberapa warna lokal tambahan (`0xFF14161B`, `0xFF1A1D24`, `0xFF232730`, `0xFFCCFF00`, `0xFF8C92A4`). Warna-warna tersebut ikut dipindahkan persis apa adanya agar tampilan Android dan iOS identik 100%.

### C. Token Tipografi 1:1 (`Type.kt`)

`FontFamily.SansSerif` (Roboto di Android, SF Pro di iOS) dan `FontFamily.Monospace` (Droid Sans Mono di Android, SF Mono di iOS) tersedia secara bawaan di Compose Multiplatform tanpa memerlukan file font `.ttf` tambahan.

| Token / Slot | `FontFamily` | `FontWeight` | `fontSize` / `lineHeight` | Warna |
| :--- | :--- | :--- | :--- | :--- |
| `MonoMetricLarge` | `Monospace` | `Bold` | `28.sp` / `34.sp` | `TextPrimary` |
| `MonoMetricMedium` | `Monospace` | `SemiBold` | `18.sp` / `24.sp` | `TextPrimary` |
| `MonoTimerDisplay` | `Monospace` | `Bold` | `36.sp` / `42.sp` | `VoltAccent` |
| `displayLarge` | `Monospace` | `Bold` | `36.sp` / `42.sp` | `TextPrimary` |
| `displayMedium` | `Monospace` | `Bold` | `28.sp` / `34.sp` | `TextPrimary` |
| `displaySmall` | `Monospace` | `SemiBold` | `22.sp` / `28.sp` | `TextPrimary` |
| `headlineLarge` | `SansSerif` | `Bold` | `26.sp` / `32.sp` | `TextPrimary` |
| `headlineMedium` | `SansSerif` | `Bold` | `22.sp` / `28.sp` | `TextPrimary` |
| `headlineSmall` | `SansSerif` | `SemiBold` | `18.sp` / `24.sp` | `TextPrimary` |
| `titleLarge` | `SansSerif` | `SemiBold` | `18.sp` / `24.sp` | `TextPrimary` |
| `titleMedium` | `SansSerif` | `Medium` | `16.sp` / `22.sp` | `TextPrimary` |
| `titleSmall` | `SansSerif` | `Medium` | `14.sp` / `20.sp` | `TextMuted` |
| `bodyLarge` | `SansSerif` | `Normal` | `16.sp` / `22.sp` | `TextPrimary` |
| `bodyMedium` | `SansSerif` | `Normal` | `14.sp` / `20.sp` | `TextPrimary` |
| `bodySmall` | `SansSerif` | `Normal` | `12.sp` / `16.sp` | `TextMuted` |
| `labelLarge` | `SansSerif` | `SemiBold` | `14.sp` / `20.sp` | `TextPrimary` |
| `labelMedium` | `SansSerif` | `Medium` | `12.sp` / `16.sp` | `TextMuted` |
| `labelSmall` | `Monospace` | `Medium` | `11.sp` / `14.sp` | `TextMuted` |

---

## 5. Pembagian Backlog Agile (Sprint 2 – Sprint 8)

| Sprint | Fokus & Deliverable Utama | Definition of Done (Blackbox) |
| :--- | :--- | :--- |
| **Sprint 2**<br>Fondasi Modul KMP & Bootstrap iOS | • Update `libs.versions.toml` & Gradle wrapper ke versi kompatibel K2.<br>• Tambah modul `:composeApp` (`commonMain`, `androidMain`, `iosMain`) dan `iosApp/` (`iOSApp.swift`, `ContentView.swift`, `MainViewController.kt`).<br>• Pertahankan `:app` sebagai Android wrapper tipis memanggil `GymTrackerApp()`. | • `./gradlew testDebugUnitTest assembleDebug assembleRelease` sukses tanpa mengubah `.github/workflows/android.yml`.<br>• Proyek `iosApp` terkompilasi dan menampilkan layar awal `#0B0C0E` di iOS Simulator tanpa crash. |
| **Sprint 3**<br>Data Layer, Domain Pure Kotlin & Backup | • Pindah `Entities.kt`, `GymDao.kt`, `GymDatabase.kt` ke Room KMP (`2.7.2` + `BundledSQLiteDriver`) dengan `PlatformDatabaseBuilder`.<br>• Pindah `SessionPreferences.kt` ke DataStore Core dengan `PlatformPreferencesPath`.<br>• Migrasi `WorkoutCalculator`, `StreakEvaluator`, `EmailOtpManager`, dan `GymRepository` ke `kotlinx-datetime`.<br>• Migrasi `GymBackupManager` ke `kotlinx-serialization-json` (skema v1). | • Seluruh unit test di `commonTest` lulus di JVM dan iOS Simulator test.<br>• Data profil, plan, dan sesi latihan bertahan setelah aplikasi di-force-close dan dibuka kembali di Android & iOS.<br>• File JSON backup yang diekspor dari Android berhasil diimpor utuh di iOS (dan sebaliknya). |
| **Sprint 4**<br>Tema 1:1, Aset SVG, Intro & Navigasi | • Pindah `Color.kt`, `Type.kt`, `Theme.kt`, `AppIcons.kt`, `BottomNavTab.kt`, `MFitOpeningIntro.kt`, dan `MainScaffold.kt` (Navigation CMP `2.9.2`).<br>• Pasang `ic_gymtracker_mark.xml` di `composeResources` dan `AppIcon-1024.png` (`#0B0C0E`) di `AppIcon.appiconset`.<br>• Hubungkan `playIntroImpact` pada `RestTimerController` bridge. | • Ikon aplikasi di Home Screen iOS Simulator & Android tampil sesuai desain dengan latar `#0B0C0E`.<br>• Animasi opening intro selesai dan bertransisi ke alur aplikasi.<br>• 5 tab bottom navigation berpindah lancar dan tidak terpotong oleh notch/Dynamic Island atau Home Indicator. |
| **Sprint 5**<br>Onboarding, AuthBridge, Home & Weekly Plan | • Implementasi `AuthBridge` Android dan iOS offline-first.<br>• Pindah `WelcomeScreen`, `SignInScreen`, `ProfileSetupScreen`, `AuthOnboardingViewModel`, `HomeScreen`, `HomeViewModel`, `WorkoutPlanScreen`, dan `WorkoutPlanViewModel`. | • Pengguna baru di iOS (tanpa `GoogleService-Info.plist`) dapat menyelesaikan Welcome → Email OTP (kode muncul di notifikasi lokal) → Profile Setup → masuk Home.<br>• Memilih tanggal di strip mingguan Home memperbarui header tanggal dan jadwal hari.<br>• Menyimpan jadwal via wizard Weekly Plan langsung tampil di Home dan bertahan setelah restart. |
| **Sprint 6**<br>Active Workout & RestTimer Cross-Platform | • Pindah `WorkoutScreen` dan `WorkoutViewModel` ke `commonMain`.<br>• Implementasi penuh `PlatformRestTimerBridge` Android (`RestTimerService` + `GymAlarmPlayer`) dan iOS (`UNUserNotificationCenter` + `AVAudioSession` ducking). | • Start Workout menjalankan stopwatch dan estimasi kalori.<br>• Menambah set Warmup/Normal/Final memicu countdown `01:00` / `02:00` / `03:00`; tombol `-15s`, `+15s`, dan `Skip` berfungsi normal.<br>• Saat aplikasi di-background pada Android & iOS, notifikasi selesai muncul tepat waktu dan alarm audio mengecilkan musik latar tanpa mematikannya.<br>• Finish Workout menyimpan set, memperbarui kalori Home, dan menaikkan streak. |
| **Sprint 7**<br>Statistics Multiplatform & Settings Backup | • Pindah `StatsScreen` (ganti `nativeCanvas.drawText` / `android.graphics.Paint` ke Compose `TextMeasurer` + `drawText`) dan `StatsViewModel`.<br>• Pindah `SettingsScreen` dan hubungkan ke `JsonBackupController` (Android SAF vs iOS Share Sheet / `UIDocumentPickerViewController`). | • Grafik e1RM menampilkan nilai dan tanggal yang terbaca jelas, serta node chart dan body heatmap Front/Back merespons sentuhan di iOS & Android.<br>• Edit Nama/Berat/Tinggi di Settings langsung terbarui di seluruh layar.<br>• Export JSON membuka SAF (Android) / Share Sheet (iOS) dan Import JSON memulihkan data dengan sukses. |
| **Sprint 8**<br>Uji Blackbox End-to-End & Kesiapan Rilis | • Eksekusi skenario blackbox lengkap lintas Android & iOS (device & simulator).<br>• Verifikasi paritas warna Dark UI `#0B0C0E` / `#D4FF00`, izin sistem, dan stabilitas background.<br>• Validasi build rilis Android & iOS. | • Seluruh skenario end-to-end (Onboarding → Weekly Plan → Workout + Rest Timer background → Stats → Export/Import JSON → Restart) lulus 100% di Android dan iOS tanpa crash.<br>• CI `.github/workflows/android.yml` hijau dan menghasilkan `MFit.apk` & `MFit-debug.apk`.<br>• Build release/archive `iosApp` di Xcode berhasil tanpa error. |
