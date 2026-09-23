========================================================
 MyMcD Shifts 1.1.0 — Android app (APK)
========================================================

WHAT IT DOES
------------
- Sign in to next.mymcd.eu with your e-mail + password
  (stored encrypted on-device with Android Keystore)
- Shows your next 10 shifts in the app
- Home-screen widget shows the next 3 shifts
- Auto-refresh on your chosen interval (5–120 min)
- Notifications: shift changes + day-of / day-before
  reminders (defaults 08:00 and 22:00; configurable)
- Share shift / add to calendar
- Language: Auto / Čeština / English — changes instantly
- EULA / Terms / Privacy in Settings → Legal

INSTALL (sideload)
------------------
1. Copy MyMcD-shifts.apk to your phone
   (Files app, Google Drive, cable, etc.)
2. Open the APK file
3. If prompted, allow "Install unknown apps" for your
   file manager / browser, then install again
4. If Play Protect warns: choose "Install anyway"
   (the APK is self-signed, not from Play Store)
5. Open "MyMcD Shifts", enter e-mail + password, Sign in
6. Long-press home screen → Widgets → search "MyMcD"
   → add "MyMcD Shifts" (next 3 shifts)

REQUIREMENTS
------------
- Android 12.0+ (API 31) — works on Android 16
- Internet connection
- Allow notifications for shift changes/reminders
- For timely widget updates: allow battery optimization
  exemption for the app if your phone is aggressive
  (Settings → Apps → MyMcD Shifts → Battery → Unrestricted)

SECURITY
--------
- Password is encrypted with a hardware-backed
  Android Keystore AES-256-GCM key
- Session cookies and shift cache are encrypted too
- Nothing is uploaded anywhere except next.mymcd.eu
- "Log out" wipes credentials from the device

REBUILD FROM SOURCE
--------------------
Source: MyMcD-shifts-source.zip
1. Install Android Studio (or SDK cmdline-tools)
2. Open the project, let Gradle sync
3. Build → Generate Signed APK (keystore is included;
   see keystore.properties — password: mymcd-shifts-2026)
   OR: ./gradlew assembleRelease

API endpoints used
------------------
POST https://mymcd.eu/user/login-check/
     (_username, _password, redir)
GET  https://next.mymcd.eu/api/user/me
GET  https://next.mymcd.eu/api/next-shifts/{id}?count=10

Package: eu.mymcd.shifts
Version: 1.1.0 (versionCode 2)
Min SDK: 31   Target SDK: 36 (Android 16)
