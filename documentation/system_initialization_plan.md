# System Initialization Plan: Yomi (Document Workspace & Reader)

## 1. Executive Summary & App Overview

**Yomi** (読み - "reading") is a clean, subscription-free, offline document workspace and reader application for Android. It enables users to discover, organize, search, and view multiple document formats within a unified Material Design 3 interface without requiring cloud synchronization, accounts, or paid tiers.

### Core Value Proposition
- **100% Offline-First:** Zero reliance on remote backends; user documents stay securely on-device.
- **Unified Document Hub:** Consolidates PDFs, Word (`.docx`), Excel (`.xlsx`), and PowerPoint (`.pptx`) files in one workspace.
- **Distraction-Free Reading:** Immersive viewing mode that auto-hides navigation and system bars, paired with dynamic and comfortable pastel themes.

---

## 2. Technical Stack Specification

| Category | Component / Library | Rationale |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.0+ | Modern language features, coroutine support, and type safety |
| **UI Framework** | Jetpack Compose + Material Design 3 | Declarative UI, reactive state binding, dynamic Material You theming |
| **Architecture** | MVVM + Clean Architecture | Unidirectional Data Flow (UDF), separation of concerns (`Data`, `Domain`, `Presentation`) |
| **Reactive State** | Kotlin Coroutines & `StateFlow` / `SharedFlow` | Asynchronous stream processing and UI state emission |
| **Dependency Injection** | Dagger Hilt 2.5x + KSP | Standardized Android DI with lifecycle-aware scoping |
| **Local Database** | Room 2.6+ with KSP | Local SQLite database for document metadata, recents, and favorites |
| **Background Processing** | WorkManager 2.9+ (Hilt Worker) | Guaranteed storage scans and index synchronization across app restarts |
| **Image Loading** | Coil Compose 2.7+ | Lightweight asynchronous image and thumbnail loading |
| **Networking** | Retrofit 2.11+ & OkHttp 4.12+ | Extensibility for document metadata lookup or future sync features |
| **PDF Rendering** | Android Native `PdfRenderer` / `androidx.pdf:pdf-viewer` | Hardware-accelerated native PDF page rendering and zoom support |
| **Office File Parser** | Apache POI 5.2+ (OOXML) | Parsing `.docx`, `.xlsx`, and `.pptx` documents locally |

---

## 3. Environment & Local Toolchain Setup

### Current Machine Status
- **Operating System:** Windows 11 / 64-bit
- **Java Detected:** Java SE 24 (`C:\Program Files\Common Files\Oracle\Java`)
- **Android SDK / Studio:** Not yet detected on standard system paths.

### Required Installations
1. **Android Studio (Ladybug 2024.2+ or newer):**
   - Install Android Studio to acquire the Android SDK, `platform-tools` (`adb`), build tools, and emulator engines.
2. **JDK 17 or JDK 21 LTS (Critical Requirement):**
   > [!IMPORTANT]
   > Android Gradle Plugin (AGP 8.x) does **not** currently support JDK 24. Gradle builds will fail under Java 24.
   > You must configure Android Studio and Gradle to use **JDK 17** or **JDK 21** (e.g., the JetBrains Runtime `jbr-17`/`jbr-21` bundled with Android Studio).
3. **Android SDK Packages:**
   - **Compile SDK:** API 35 (Android 15) or API 34 (Android 14)
   - **Target SDK:** API 34 / 35
   - **Min SDK:** API 26 (Android 8.0 Oreo - required for modern storage, notification, and POI features)
   - **Build-Tools:** 34.0.0 or 35.0.0

---

## 4. Gradle Configuration & Dependency Catalog

### A. Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
agp = "8.7.0"
kotlin = "2.0.20"
ksp = "2.0.20-1.0.25"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
hiltWork = "1.2.0"
room = "2.6.1"
work = "2.9.1"
composeBom = "2024.09.03"
lifecycle = "2.8.6"
navigation = "2.8.2"
coil = "2.7.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
poi = "5.2.5"
desugar = "2.1.2"

[libraries]
# Core & Lifecycle
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.13.1" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.9.2" }

# Compose & Material 3
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Dagger Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }
hilt-work-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }

# Room
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# WorkManager
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }

# Networking & Image Loading
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
retrofit-core = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }

# Office Parsing (Apache POI)
apache-poi = { group = "org.apache.poi", name = "poi", version.ref = "poi" }
apache-poi-ooxml = { group = "org.apache.poi", name = "poi-ooxml", version.ref = "poi" }

# Java 8+ Desugaring
desugar-jdk-libs = { group = "com.android.tools", name = "desugar_jdk_libs", version.ref = "desugar" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### B. App-Level Build Script (`app/build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.yomi.reader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yomi.reader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AndroidX & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coil & Networking
    implementation(libs.coil.compose)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)

    // Apache POI for Office Formats (.docx, .xlsx, .pptx)
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml)

    // Debugging
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

---

## 5. Required Android Manifest Permissions & Scoped Storage

In `AndroidManifest.xml`, configure the permissions for scanning files and managing storage across different Android versions:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- For devices running Android 12 (API 32) and below -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <!-- Storage Access Framework and Media permissions -->
    <!-- Optional: for deep background scanning across all shared storage -->
    <!-- Note: Google Play has strict policies on MANAGE_EXTERNAL_STORAGE; use SAF for store compliance if needed -->
    <uses-permission android:name="android.permission.READ_MEDIA_DOCUMENTS" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".YomiApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Yomi">
        ...
    </application>
</manifest>
```

---

## 6. Project Architecture & Package Structure

```text
com.yomi.reader/
├── YomiApplication.kt                  # Hilt AndroidApp entrypoint & WorkManager init
├── di/                                 # Hilt Dependency Injection Modules
│   ├── DatabaseModule.kt               # Provides Room Database and DAOs
│   ├── RepositoryModule.kt             # Binds repository interfaces to implementations
│   └── WorkerModule.kt                 # WorkManager configuration
├── data/
│   ├── local/
│   │   ├── YomiDatabase.kt             # Room Database definition
│   │   ├── dao/
│   │   │   └── DocumentDao.kt          # CRUD queries, Flow queries, favorites, recents
│   │   └── entity/
│   │       └── DocumentEntity.kt       # id, uri, title, fileType, size, lastModified, isFavorite, lastOpened
│   ├── repository/
│   │   └── DocumentRepositoryImpl.kt   # Interacts with Room & WorkManager
│   └── worker/
│       └── StorageScannerWorker.kt     # Background scanner for .pdf, .docx, .xlsx, .pptx
├── domain/
│   ├── model/
│   │   ├── Document.kt                 # Domain data model
│   │   └── DocumentCategory.kt         # Enum (ALL, PDF, WORD, EXCEL, PRESENTATION)
│   ├── repository/
│   │   └── DocumentRepository.kt       # Repository contract
│   └── usecase/
│       ├── GetDocumentsUseCase.kt
│       ├── SearchDocumentsUseCase.kt
│       ├── ToggleFavoriteUseCase.kt
│       └── ParseDocumentContentUseCase.kt
├── parser/                             # Extraction & Rendering Engines
│   ├── pdf/
│   │   └── PdfPageRenderer.kt          # Native Android PdfRenderer wrapper
│   ├── docx/
│   │   └── DocxContentParser.kt        # Apache POI XWPFDocument text extractor
│   ├── xlsx/
│   │   └── XlsxSheetParser.kt          # Apache POI XSSFWorkbook tabular extractor
│   └── pptx/
│       └── PptxSlideParser.kt          # Apache POI XMLSlideShow slide extractor
└── ui/
    ├── theme/
    │   ├── Color.kt                    # Pastel palette (Lavender, Mint, Peach, Cream)
    │   ├── Theme.kt                    # MaterialTheme + Dynamic Color support
    │   └── Type.kt                     # Typography definitions
    ├── navigation/
    │   ├── NavGraph.kt                 # Compose Navigation routes
    │   └── Screen.kt                   # Sealed class for screen destinations
    ├── dashboard/
    │   ├── DashboardScreen.kt          # Main home screen
    │   ├── DashboardViewModel.kt       # StateFlow handling filter chips, search, recents
    │   └── components/
    │       ├── DocumentCard.kt
    │       ├── FilterChipRow.kt
    │       └── SearchTopBar.kt
    └── viewer/
        ├── PdfViewerScreen.kt          # Zoomable PDF layout with page indicator
        ├── DocxViewerScreen.kt         # Clean reading layout for Word docs
        ├── XlsxViewerScreen.kt         # Scrollable table/grid for spreadsheets
        └── PptxViewerScreen.kt         # Slide carousel for presentations
```

---

## 7. Pastel Design Palette Token Reference

- **Primary (`#7E8CE0` / Soft Lavender):** Top bars, active filter chips, primary action buttons.
- **Secondary (`#76A99A` / Muted Mint):** Badges for spreadsheets, completed indexing states, subtle accents.
- **Tertiary (`#E59880` / Warm Peach):** Favorite icons, bookmark pins, warning/callout elements.
- **Background (`#FBF9F5` / Warm Cream):** Soft, paper-like background for prolonged, glare-free reading.
- **Surface (`#FFFFFF` in light, `#23242A` in dark):** Card surfaces, sheet backdrops, dialogs.
- **Dark Background (`#1A1B20`):** Soft dark gray for OLED-friendly reading without harsh black-white contrast.

---

## 8. Implementation Roadmap (Phases)

1. **Phase 1: Project Initialization & Tooling Setup**
   - Configure Gradle build scripts, Version Catalog (`libs.versions.toml`), KSP, and Hilt.
   - Configure desugaring and packaging options for Apache POI.
2. **Phase 2: Data Layer & Background Indexing**
   - Create Room Database (`DocumentEntity`, `DocumentDao`).
   - Implement `StorageScannerWorker` with WorkManager for discovering local documents.
   - Implement Document Repository and Use Cases with Kotlin Flow.
3. **Phase 3: Material 3 UI & Dashboard**
   - Set up the pastel color scheme and dynamic Material You theme.
   - Build `DashboardScreen` with search filtering, format chips, and recent cards.
4. **Phase 4: Document Viewers**
   - Implement native `PdfRenderer` viewer with pinch-to-zoom.
   - Implement `DocxContentParser`, `XlsxSheetParser`, and `PptxSlideParser` via Apache POI.
   - Implement immersive mode (fullscreen reading).
5. **Phase 5: Polish & Performance Optimization**
   - Add bookmarks/favorites persistence.
   - Add lazy loading and memory pooling for large office files and multi-page PDFs.
