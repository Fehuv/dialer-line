# Troubleshooting

## The selector never appears

- Verify LSPosed scope is exactly `com.google.android.dialer`
- Reboot, or force-stop Phone by Google after enabling or updating the module
- Confirm the device really has two call-capable outgoing accounts
- Check LSPosed logs for tags starting with `DialerLine/`

## The selector appears on the wrong screen

- The keypad detector is heuristic and view-tree based
- If the Phone by Google UI changes significantly, the keypad detection logic may need retuning

## The selector appears but the call still uses the wrong SIM

- Verify you are placing a normal `tel:` call
- Check LSPosed logs for:
  - `DialerLine/TelecomHook`
  - `DialerLine/IntentHook`
- Confirm the desired line is still available in Android's call-capable accounts

## One SIM disappeared after reboot or removal

- The module re-resolves the saved account at runtime
- If the previously saved account no longer exists, it automatically falls back to the first available account

## The selector is missing on a single-SIM device

That is expected. The selector is hidden when there is only one usable outgoing calling account.

## Emergency behavior

Emergency calls are intentionally left to stock Android behavior. The module does not override them.

## Logs

Look for log tags with the `DialerLine/` prefix in LSPosed logs to diagnose:

- module loading
- keypad detection
- account resolution
- call interception
