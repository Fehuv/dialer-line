# Manual Test Plan

## Dual-SIM basics

1. Set up a physical SIM + eSIM, or any two active call-capable lines
2. Open Phone by Google
3. Open the keypad screen
4. Verify the selector appears beside the call button

## SIM 1 selected then call

1. Select the first available line
2. Enter a normal phone number
3. Tap Call
4. Verify the call uses the first selected outgoing line

## SIM 2 selected then call

1. Toggle to the second available line
2. Enter a normal phone number
3. Tap Call
4. Verify the call uses the second selected outgoing line

## Persistence

1. Select a line
2. Force-stop Phone by Google
3. Reopen the app
4. Verify the selector still reflects the prior selection

## Activity recreation

1. Open the keypad
2. Rotate the device or otherwise recreate the activity
3. Verify the selector remains visible
4. Verify the selection is preserved

## Remove one SIM

1. Disable or remove one calling line
2. Reopen Phone by Google
3. Verify the selector hides cleanly or falls back safely to the only remaining account

## Emergency number behavior

1. Enter a local emergency number
2. Do not complete a real emergency test call
3. Verify through logs/code path review that the module bypasses account override

## Special dial strings

1. Enter a dial string containing `*` or `#`
2. Verify the selected line is still respected if the dialer routes it through the normal outgoing-call path

## Single-SIM behavior

1. Test on a single-SIM device or profile
2. Verify the selector does not appear
