# Build and Development

## Requirements

- Android SDK 34
- JDK 17
- Android Studio or a recent Gradle-compatible Android build environment
- LSPosed/libxposed modern API support

## Dependency

This project uses the published libxposed artifact from Maven Central:

```kotlin
compileOnly("io.github.libxposed:api:101.0.0")
```

No manual AAR drop is required.

## Build

For local development:

```bash
./gradlew :app:assembleDebug
```

For install onto a connected device:

```bash
./gradlew :app:installDebug
```

## Recommended Install Workflow

For LSPosed development, prefer Gradle installs over Android Studio deploy optimization:

```bash
./gradlew :app:installDebug
```

That avoids stale module APK issues where Android Studio reports success but the device still runs an older package.

## Enable in LSPosed

1. Install the APK
2. Enable the module in LSPosed
3. Scope it only to `com.google.android.dialer`
4. Reboot, or force-stop Phone by Google before testing

## Code Layout

```text
app/src/main/java/dev/yasir/dialerline/
  DialerLineModule.kt
  account/
  guard/
  hook/
  state/
  ui/
  util/
```

## Development Notes

- The module is intentionally scoped to the dialer process only
- UI injection is programmatic; there are no resource hooks
- Outgoing-call routing is centered around public telecom APIs
- Fail-open behavior is preferred over breaking the dialer
