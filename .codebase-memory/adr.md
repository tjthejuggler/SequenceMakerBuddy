## Verification: Moved out of dialog, single button on main screen (added 2026-05-11T09:15+01:00)

The first verification implementation lived inside the calibration dialog with TWO buttons ("🔁 Test Sync" using current applied delay, "↩ Use & Test" applying the freshly computed delay first). User feedback: the two-button choice was confusing, and verification belongs **alongside the slider**, not inside a dialog — the user wants to manually fine-tune with the slider and verify in the same UI.

### Changes

- **Removed from CalibrationDialog**: the entire verification section (both buttons + helper text + "Currently applied" line) and the dead `VerifyRunning` composable. Results screen is now just: stats → recommended delay → Save Preset → Run Again.
- **Added to DelayControls** (main screen):
  - A row with two side-by-side buttons: `🎯 Calibrate` and `🔁 Verify Sync`. The Verify button toggles to `■ Stop Verify` (error color) while RUNNING_VERIFY.
  - An **inline flash square** (the same `FlashSquare` composable used in the dialog, now `internal` so DelayControls can render it) appears below the buttons during verification, with a live status line: "🔁 Verifying with delay ±X.XXs  •  beep N / 3".
  - Tooltip reminder that slider changes apply on the NEXT verify run (not mid-run).
- **ViewModel wrappers**: `startInlineVerification(context)` calls `safeStop()` then `calibration.startVerification(context, delaySeconds.floatValue)`; `cancelInlineVerification()` calls `calibration.cancel()`.
- **PlayerScreen.kt**: passes verify state (`isVerifying`, `verifyFlashOn`, `verifyStimuliDelivered`) and callbacks (`onStartVerify`, `onCancelVerify`) down to DelayControls.
- **CalibrationDialog** still has a `RUNNING_VERIFY` branch in its `when` (defensively falls back to ResultsPhase) but normal flow no longer enters verify from inside the dialog.

This keeps the calibration dialog focused on the measurement task and puts iterative fine-tuning where the slider already is.