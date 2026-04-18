# Cellular Location App

## Project Goal
An Android app that passively listens to surrounding cellular towers and determines the phone's geographic location **without GPS**, using cell tower metadata (Cell ID, LAC, MCC, MNC, signal strength) and a lookup database/service.

## Target Device
- **Device**: Google Pixel 6a
- **OS**: Android (rooted via Magisk)
- **Architecture**: arm64-v8a
- **Chipset**: Google Tensor G1
- **Modem**: Exynos 5300 (Samsung)

## Tech Stack
- **Language**: Kotlin
- **Build System**: Gradle (Kotlin DSL)
- **Min SDK**: 31 (Android 12) — Pixel 6a ships with Android 12
- **Target SDK**: 34 (Android 14)
- **UI**: Jetpack Compose
- **Architecture**: MVVM + Repository pattern

## Key Android APIs
- `TelephonyManager` — query current/neighboring cell info
- `TelephonyCallback` (API 31+) / `PhoneStateListener` — listen for cell changes
- `CellInfoLte`, `CellInfoNr`, `CellInfoWcdma`, `CellInfoGsm` — cell metadata structs
- `READ_PHONE_STATE` permission (normal) — required for cell info
- `ACCESS_FINE_LOCATION` permission — required by Android to call `getAllCellInfo()`

## Root / Magisk Usage
Root is available but should only be used where strictly necessary:
- Accessing raw modem AT commands via `/dev/` (if needed for more precise signal data)
- Reading privileged system properties
- Bypassing Android location permission restrictions if needed

Prefer using standard Android APIs where possible to keep the app functional without root fallback paths too.

## Location Determination Strategy
1. **Primary**: Query [OpenCellID](https://opencellid.org/) or [Mozilla Location Services (MLS)](https://location.services.mozilla.com/) API using (MCC, MNC, LAC/TAC, CID)
2. **Secondary**: Local offline database (CSV/SQLite) from OpenCellID for no-network fallback
3. **Tertiary**: Basic weighted trilateration using signal strength (RSSI/RSRP) from multiple towers simultaneously

## Data Collected Per Tower
| Field | Source | Description |
|-------|--------|-------------|
| MCC | CellIdentityLte | Mobile Country Code |
| MNC | CellIdentityLte | Mobile Network Code |
| TAC/LAC | CellIdentityLte | Tracking/Location Area Code |
| CID/eNB | CellIdentityLte | Cell Identity |
| PCI | CellIdentityLte | Physical Cell ID |
| RSRP | CellSignalStrengthLte | Reference Signal Received Power (dBm) |
| RSRQ | CellSignalStrengthLte | Reference Signal Quality (dB) |
| Timestamp | System | When measurement was taken |

## Project Structure
```
app/
  src/main/
    java/com/cellularlocation/
      data/
        CellTowerRepository.kt     # Aggregates cell data from TelephonyManager
        LocationResolver.kt        # Maps cell data -> coordinates
        db/                        # Room DB for local tower cache
        api/                       # Retrofit clients for OpenCellID/MLS
      domain/
        CellTowerInfo.kt           # Data model
        LocationResult.kt          # Resolved location model
      ui/
        MainActivity.kt
        screens/MapScreen.kt       # Shows estimated location on map
        screens/TowerListScreen.kt # Lists visible towers + signal strength
        viewmodel/MainViewModel.kt
      service/
        CellScanService.kt         # Foreground service for background scanning
    res/
  AndroidManifest.xml
```

## Permissions Required
```xml
<uses-permission android:name="android.permission.READ_PHONE_STATE"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.INTERNET"/>
```

## Important Notes
- `getAllCellInfo()` requires `ACCESS_FINE_LOCATION` on API 29+ even though it returns no GPS data — this is an Android requirement, not optional.
- On the Pixel 6a with Tensor G1, the device supports LTE and NR (5G Sub-6). Both `CellInfoLte` and `CellInfoNr` may be returned.
- Cell scan interval is throttled by Android to ~1 scan/sec minimum; use `requestCellInfoUpdate()` for async updates.
- OpenCellID API key is required for lookups; store it in `local.properties`, never commit to git.

## Build & Run
```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device (USB debugging enabled)
adb install app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat -s CellularLocation
```

## Key Dependencies (build.gradle.kts)
```kotlin
// Networking
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")

// Room (local DB)
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// Compose + Maps
implementation("androidx.compose.ui:ui")
implementation("com.google.maps.android:maps-compose:4.3.3")
implementation("com.google.android.gms:play-services-maps:18.2.0")

// ViewModel / Coroutines
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```
