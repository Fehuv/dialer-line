# Architecture

## Overview

`DialerLine` is intentionally narrow in scope:

- Target package only: `com.google.android.dialer`
- No resource hooks
- No default-dialer replacement
- No global voice-SIM switching
- No mobile-data switching
- No incoming-call handling changes

The module is designed to stay inside the Phone by Google app process and work through public Android framework APIs wherever possible.

## Main Components

### Entry and process scoping

- `DialerLineModule`
  - Modern libxposed module entry
  - Registers hooks only when the target package is ready
- `DialerProcessEntrypoint`
  - Installs the dialer-process hooks once per app process

### Process context

- `ProcessContextHook`
  - Hooks `Instrumentation.callApplicationOnCreate(Application)`
  - Captures a reliable process `Context` for persistence and account resolution

### UI injection

- `DialpadUiInjector`
  - Hooks activity lifecycle entry points
  - Detects the keypad screen heuristically from the live view tree
  - Injects the SIM selector programmatically
  - Positions it beside the call button using runtime view anchoring

### Outgoing call interception

- `OutgoingCallInterceptor`
  - Coordinates the outgoing-call hook strategies
- `TelecomPlaceCallHookStrategy`
  - Primary path
  - Hooks `TelecomManager.placeCall(Uri, Bundle)`
  - Injects `TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE`
- `IntentCallHookStrategy`
  - Public-intent fallback for `ACTION_CALL` / `CALL_PRIVILEGED`

### Account and persistence

- `PhoneAccountResolver`
  - Uses `TelecomManager.getCallCapablePhoneAccounts()`
  - Builds a stable serialized key per account
  - Performs best-effort slot/account labeling via `PhoneAccount` and `SubscriptionManager`
- `SelectedAccountStore`
  - Persists the selected stable key with `SharedPreferences`
- `SimSelectionController`
  - Re-resolves persisted selections
  - Falls back to the first available account if the saved one disappears

### Safety gates

- `EmergencyAndMmiGuard`
  - Skips emergency calls
  - Skips non-`tel:` URIs

## Hook Points

The current implementation prefers framework-level hooks over fragile dialer internals:

1. `Instrumentation.callApplicationOnCreate(Application)`
2. `Instrumentation.callActivityOnResume(Activity)`
3. `Instrumentation.callActivityOnDestroy(Activity)`
4. `TelecomManager.placeCall(Uri, Bundle)`
5. `ContextWrapper.startActivity(Intent)`
6. `ContextWrapper.startActivity(Intent, Bundle)`

## UI Strategy

The selector is shown only when:

- the current activity belongs to `com.google.android.dialer`
- the visible view tree looks like a keypad
- at least two call-capable outgoing accounts are available

Dialpad detection is heuristic and intentionally avoids hardcoded resource IDs:

- scan visible clickable views
- collect visible digit and special-key tokens
- require a keypad-like button layout before showing the selector

Placement is tied to the detected call action so the control stays visually part of the keypad call area instead of acting like a detached overlay.

## Compatibility Notes

- Carrier/account labels are preferred over generic `SIM 1` / `SIM 2`
- Slot resolution is still best effort because Android/vendor metadata is not fully standardized
- Future Phone by Google UI changes may require updates to keypad or call-button detection

## Tested Assumptions

The current implementation assumes:

- Phone by Google routes normal outgoing calls through `TelecomManager.placeCall(...)` or public `ACTION_CALL` flows
- the dialer exposes a keypad that is detectable from the live view tree
- the call action remains detectable enough for runtime anchoring
- the dialer process can query the needed calling-account metadata
