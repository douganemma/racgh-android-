# RaC Ghana Android

Native Android application for RaC Ghana, a Ghana car-rental marketplace.

## Build
Open this project in Android Studio (Hedgehog or newer), allow Gradle sync, then choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

- Package: `com.racghana.app`
- Minimum Android: 8.0 (API 26)
- UI: Kotlin + Jetpack Compose + Material 3

The app currently provides a native discovery and booking-request experience. Vehicle availability, account authentication and payment confirmation remain connected to RaC Ghana's existing website/backend and must be integrated against its documented API before production release.

Website: https://racghana.com
