# RaC Ghana Android

Native Android application for RaC Ghana, a Ghana car-rental marketplace. The interface is built with Kotlin and Jetpack Compose — **no WebView is used**.

## Build
Open this project in Android Studio (Hedgehog or newer), allow Gradle sync, then choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

- Package: `com.racghana.app`
- Minimum Android: 8.0 (API 26)
- UI: Kotlin + Jetpack Compose + Material 3

The current native prototype includes vehicle discovery, vehicle detail screens, search inputs, and app navigation. It uses the publicly visible vehicle catalogue as starter content. Live availability, customer accounts, booking submission and payments are not yet wired up: those require the site's supported backend/API contract. No fake booking or payment confirmation is shown.

Website: https://racghana.com
