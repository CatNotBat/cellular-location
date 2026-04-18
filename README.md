# cellular-location

Android app that figures out where you are using cell towers instead of GPS. Reads Cell ID, LAC, MCC, MNC, and signal strength from surrounding towers, then looks up coordinates via OpenCellID or Mozilla Location Services.

Built for a rooted Pixel 6a, but the standard API path works on any Android 12+ device.

## How it works

1. `TelephonyManager.getAllCellInfo()` gives you metadata for every visible tower (LTE, 5G NR, WCDMA, GSM)
2. The app sends (MCC, MNC, TAC, CID) to a geolocation API and gets back lat/lng
3. If there's no network, it falls back to a local SQLite database of known towers
4. With multiple towers visible, it does weighted trilateration using signal strength (RSRP)

## Stack

- Kotlin, Jetpack Compose, MVVM
- Room for local tower cache
- Retrofit for API calls
- Google Maps Compose for visualization

## Building

```bash
# Set your OpenCellID API key
echo "opencellid.api.key=YOUR_KEY" >> local.properties

./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

Android requires `ACCESS_FINE_LOCATION` to call `getAllCellInfo()` even though it returns no GPS data. This is an Android restriction, not something the app actually uses for GPS.
