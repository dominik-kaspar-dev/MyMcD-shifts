# MyMcD Shifts

Android 16 (API 36) app that logs into [next.mymcd.eu](https://next.mymcd.eu), shows your next 10 shifts, and puts the next 3 on a home-screen widget.

## Features
- OAuth/login flow against mymcd.eu + next.mymcd.eu APIs
- Encrypted credential storage (Android Keystore, AES-256-GCM)
- Multi-account support (add / switch / remove)
- Next 10 shifts in-app; next 3 in the widget
- Widget ↻ manual refresh + pin-to-home-screen button
- Auto re-login when the session expires
- Shift-change notifications (added / removed / changed times)
- Previous plan history + “Show previous” toggle
- Language: Auto (system locale) / Čeština / English
- Background refresh every ~30 minutes (WorkManager)

## Build
```bash
# Requires JDK 17 + Android SDK (compileSdk 36)
cp local.properties.example local.properties   # set sdk.dir
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

### Signing
Release signing uses `keystore.properties` + `release.keystore` (not committed). Copy from your local setup if you need to reproduce the same signature.

## Install (sideload)
Copy the APK to the phone → open → allow unknown sources → install.

## API endpoints
| Method | URL |
|--------|-----|
| GET | `https://next.mymcd.eu/login` (OAuth start) |
| POST | `https://mymcd.eu/user/login-check/` (`_username`, `_password`, `redir`) |
| GET | `https://next.mymcd.eu/api/user/me` |
| GET | `https://next.mymcd.eu/api/next-shifts/{userId}?count=10` |

## Package
- `eu.mymcd.shifts`
- versionName `1.0.0` / versionCode `1`
- minSdk 31 · targetSdk 36
