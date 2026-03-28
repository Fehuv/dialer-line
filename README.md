# DialerLine

`DialerLine` is an LSPosed module for rooted Pixel phones that improves the dual-SIM experience in Phone by Google.

It adds a small SIM selector next to the call button on the keypad screen, remembers the last selected line, and routes normal outgoing calls through that selected SIM without changing Android's global default voice SIM.

## What It Does

- Shows the current outgoing line on the Phone by Google keypad
- Lets you tap to switch between available calling accounts
- Persists your selection across app relaunches
- Forces normal outgoing calls to use the selected line
- Keeps all behavior scoped to `com.google.android.dialer`

## What It Does Not Do

- It does not replace the default dialer
- It does not change Android's global default voice SIM
- It does not switch mobile data
- It does not change incoming-call behavior
- It does not use resource hooks

## Requirements

- Rooted Pixel device
- LSPosed with modern libxposed support
- Phone by Google
- Dual-SIM setup for the SIM selector to appear

## Install

1. Build and install the APK:

```bash
./gradlew :app:installDebug
```

2. Enable `DialerLine` in LSPosed.
3. Scope it only to `com.google.android.dialer`.
4. Reboot, or at minimum force-stop Phone by Google.
5. Open the keypad screen in Phone by Google.

## Use

- Open the keypad screen
- Tap the SIM selector next to the call button to switch lines
- Dial a normal phone number and place the call

On single-SIM devices, or when only one calling account is available, the selector stays hidden.

## Notes

- Emergency calls are left to stock behavior
- Labels prefer carrier/account names and fall back to `SIM n` only when needed
- The current UI is tuned for the tested Pixel + Phone by Google layout, so future Google Dialer updates may require layout adjustments

## Docs

- [Architecture](docs/architecture.md)
- [Build and Development](docs/development.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Manual Test Plan](docs/manual-test-plan.md)

## Project Structure

```text
app/
  src/main/java/dev/yasir/dialerline/
    DialerLineModule.kt
    account/
    guard/
    hook/
    state/
    ui/
    util/
  src/main/resources/META-INF/xposed/
    java_init.list
    module.prop
    scope.list
docs/
  architecture.md
  development.md
  troubleshooting.md
  manual-test-plan.md
```
