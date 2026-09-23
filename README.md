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
- Optional reminders: day-of (default 08:00) and day-before (default 22:00)
- Configurable refresh interval (5–120 min) and notification toggles
- Previous plan history + “Show previous” toggle
- Share shift / add to calendar
- Language switch applies instantly (activity recreate, no restart)
- Language: Auto (system locale) / Čeština / English
- EULA / Terms / Privacy (en + cs) in Settings → Legal
- Background refresh on WorkManager (user-configured interval)

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

## Legal
Bundled full texts: `app/src/main/assets/legal/` (en + cs).
Repo copies for publishing: `docs/EULA.md`, `docs/TERMS.md`, `docs/PRIVACY_POLICY.md`.

## Package
- `eu.mymcd.shifts`
- versionName `1.1.1` / versionCode `3`
- minSdk 31 · targetSdk 36
