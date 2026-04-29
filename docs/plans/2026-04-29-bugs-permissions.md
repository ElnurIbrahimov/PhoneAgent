# PhoneAgent — Bugs + Permission Flows Plan

**Goal:** Fix MediaProjection single-use bug, add runtime permission requests, guide users to enable Accessibility Service.

**Architecture:** Minimal changes — cache MediaProjection in ScreenCaptureManager, add permission launchers to MainActivity + PermissionScreen, add Accessibility settings intent.

---

### Task 1: Fix MediaProjection single-use bug

**Files:** `perception/ScreenCaptureManager.kt`

The bug: `getMediaProjection(resultCode, resultData)` consumes the Intent — can only be called once. After that, subsequent `captureScreenshot()` calls fail silently.

Fix: Create MediaProjection once in `onActivityResult` and reuse it. Only stop on `stopCapture()`.

- [ ] In `onActivityResult`, create and store `mediaProjection` immediately
- [ ] In `captureScreenshot`, remove `getMediaProjection()` call, use stored instance  
- [ ] Don't stop `mediaProjection` in `releaseResources()` — only release virtual display and image reader
- [ ] Stop `mediaProjection` only in `stopCapture()`

### Task 2: Add runtime permission flows

**Files:** `MainActivity.kt`, `PermissionScreen.kt`, `AndroidManifest.xml`

Add `ActivityResultContracts.RequestMultiplePermissions` launcher for SMS + CALL permissions (needed at runtime on API 23+). Also add RECORD_AUDIO.

- [ ] In `MainActivity`, add permission launcher for `SEND_SMS`, `CALL_PHONE`, `RECORD_AUDIO`
- [ ] In `PermissionScreen`, add buttons to trigger each permission request
- [ ] Show current grant status for each permission

### Task 3: Accessibility Service enable guide

**Files:** `PermissionScreen.kt`

Add a button that opens `Settings > Accessibility` so users can enable the PhoneAgent accessibility service.

- [ ] Add intent: `Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)` 
- [ ] Add check: `isAccessibilityServiceEnabled()` using `Settings.Secure`

---

### Task 4: Build & verify
