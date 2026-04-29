# PhoneAgent — Remaining Fixes Plan

> **Goal:** Fix all remaining medium/high-severity issues from the audit that were deferred in the first pass.

**Architecture:** Surgical fixes to existing files. No new abstractions. Fixes grouped into 5 parallel waves.

**Tech Stack:** Kotlin, Android SDK, OkHttp, Room

---

### Task 1: `onActivityResult` → Activity Result API

**Files:** `MainActivity.kt`

Replace deprecated `onActivityResult` with `registerForActivityResult(StartActivityForResult())`. 
- Add a launcher field that captures the result and routes to `ScreenCaptureManagerImpl.onActivityResult`
- Remove the `onActivityResult` override entirely
- This also fixes the tight coupling — the launcher's callback handles the routing, not the Activity

```kotlin
// Add field before onCreate:
private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    (agentController.getScreenCaptureManager() as? ScreenCaptureManagerImpl)?.onActivityResult(
        ScreenCaptureManagerImpl.REQUEST_CODE, result.resultCode, result.data
    )
}

// Remove onActivityResult override entirely (lines 41-44)
```

- [ ] Modify `MainActivity.kt` — add launcher field, remove `onActivityResult`
- [ ] Run `./gradlew :app:testDebugUnitTest` — verify
- [ ] Run `./gradlew :app:assembleDebug` — verify

---

### Task 2: Browser + WebView fixes (3 files)

**Files:** `BrowserSessionManager.kt`, `BrowserTool.kt`, `AgentWebView.kt`

**BrowserSessionManager — thread safety:**
- Add `@Synchronized` to all public methods (setActiveWebView, getActiveWebView, hasActiveBrowser, openUrl, canGoBack, goBack, reload, clear)
- Remove side-effect `clear()` from `getActiveWebView()` getter — just return null without clearing
- `isActive` field: remove `@Volatile` (redundant with `@Synchronized`)

**BrowserTool — context leak:**
- Change `context: Context` to store `context.applicationContext`

**AgentWebView — cleanup:**
- Remove `databaseEnabled = true` (dead config, WebSQL removed from Chromium)
- Remove `setAcceptThirdPartyCookies(this, true)` (deprecated since API 33, privacy risk)
- Add cleanup in `update` composable: on `DisposableEffect` to clear browser session when composable leaves composition

- [ ] Modify 3 files
- [ ] Run `./gradlew :app:testDebugUnitTest` — verify
- [ ] Run `./gradlew :app:assembleDebug` — verify

---

### Task 3: Provider + Keystore hardening (4 files)

**Files:** `ModelRouter.kt`, `ProviderRepository.kt`, `AndroidKeystoreSecretStore.kt`, `OcrManager.kt`

**ModelRouter — non-exhaustive when + fail-open:**
- Add `else -> throw ProviderError.UnknownError("Unsupported provider type: ${config.type}")` to `createProvider`
- Replace fail-open `?: CrofAiDefaults.DEFAULT_CONFIG` in `getProviderForModel` with a descriptive exception

**ProviderRepository — save-before-init:**
- In `saveProvider()`, if `prefs[providersKey]` is null (not yet initialized), merge with defaults before saving:
```
val current = if (prefs[providersKey] != null) parseProvidersJson(prefs[providersKey]!!).toMutableList()
    else defaultProviders.toMutableList()
```

**AndroidKeystoreSecretStore — blocking calls + error handling:**
- Wrap all `suspend` function bodies in `withContext(Dispatchers.Default)`
- Wrap `KeyStore.getInstance()` in try/catch — if keystore unavailable, throw a descriptive `IllegalStateException` instead of crashing in class init
- In `getSecret`: do NOT auto-delete on decryption failure — log and return null instead
- Change `prefs: SharedPreferences` to use `context.getSharedPreferences("phoneagent_secrets", Context.MODE_PRIVATE)` with a namespaced filename

**OcrManager — lifecycle:**
- Add `fun close()` to `OcrManager` interface and `OcrManagerImpl`
- Call `recognizer.close()` in `close()`
- Call `ocrManager.close()` in `AgentController.destroy()`

- [ ] Modify 4 files
- [ ] Run `./gradlew :app:testDebugUnitTest` — verify
- [ ] Run `./gradlew :app:assembleDebug` — verify

---

### Task 4: Data model + prompt + overlay fixes (5 files)

**Files:** `AgentRequest.kt`, `AgentResponse.kt`, `AgentPromptBuilder.kt`, `OverlayService.kt`, `TaskHistoryManager.kt`

**AgentRequest — dead field:**
- Remove unused `val history: List<ChatMessage>? = null` field
- Remove history handling from `BaseOpenAiProvider.buildRequestBody()` (the `!request.history.isNullOrEmpty()` block)

**AgentResponse — mis-typed usage field:**
- Change `val usage: String?` to `val usage: Map<String, Int>?` (or remove the field if unused)
- Update `BaseOpenAiProvider.parseResponse()` to parse usage into a map

**AgentPromptBuilder — truncation indicator:**
- When truncating observation to 4000 chars, append `...[truncated]` suffix
- Only append if actual length exceeds 4000

**OverlayService — race condition + stop foreground:**
- In `stop()` companion function, send an Intent with action `"STOP"` that `onStartCommand` handles by calling `stopForeground(STOP_FOREGROUND_REMOVE)` and `stopSelf()`
- Alternatively, simpler: just add a `stopForeground` call before `stopService`:
```
fun stop(context: Context) {
    context.stopService(Intent(context, OverlayService::class.java))
}
```
Add `stopForeground(STOP_FOREGROUND_REMOVE)` in `onDestroy` (already exists, just needs the call before super.onDestroy)

**TaskHistoryManager — error resilience:**
- Wrap DB calls in try/catch with logging (use `android.util.Log`)
- Don't let DB failures crash the agent loop

- [ ] Modify 5 files
- [ ] Run `./gradlew :app:testDebugUnitTest` — verify
- [ ] Run `./gradlew :app:assembleDebug` — verify

---

### Task 5: Gradle wrapper upgrade + PageExtractor fix

**Files:** `gradle/wrapper/gradle-wrapper.properties`, `PageExtractor.kt`

**Gradle wrapper 8.4 → 8.10:**
- Change `distributionUrl` in `gradle-wrapper.properties` from `gradle-8.4-bin.zip` to `gradle-8.10.3-bin.zip`

**PageExtractor — surrogate pair safety:**
- In `extractVisibleSummary`: replace `txt.substring(0, 2000)` with a code-point-safe version
- Add `...[truncated]` indicator when truncating
- Add `...[N more items]` indicator when `Math.min(...)` drops elements

- [ ] Modify 2 files
- [ ] Run `./gradlew :app:testDebugUnitTest` — verify (will download new Gradle)
- [ ] Run `./gradlew :app:assembleDebug` — verify

---

### Task 6: Full build verification

- [ ] Run `./gradlew clean testDebugUnitTest` — all tests pass
- [ ] Run `./gradlew assembleDebug` — APK builds
- [ ] Verify no warnings (except `onActivityResult` which we removed)

---

### Summary of Changes

| # | Files | Category |
|---|-------|----------|
| 1 | `MainActivity.kt` | Modernization |
| 2 | `BrowserSessionManager.kt`, `BrowserTool.kt`, `AgentWebView.kt` | Security/Reliability |
| 3 | `ModelRouter.kt`, `ProviderRepository.kt`, `AndroidKeystoreSecretStore.kt`, `OcrManager.kt` | Security/Reliability |
| 4 | `AgentRequest.kt`, `AgentResponse.kt`, `AgentPromptBuilder.kt`, `OverlayService.kt`, `TaskHistoryManager.kt` | Cleanup/Reliability |
| 5 | `gradle-wrapper.properties`, `PageExtractor.kt` | Upgrade/Fix |
| 6 | Build verification | Verification |
