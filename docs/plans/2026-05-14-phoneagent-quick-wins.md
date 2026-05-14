# PhoneAgent Quick Wins Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement 6 high-impact reliability improvements, each fixable in 1-2 hours.

**Architecture:** Each fix is isolated to its component. No architectural changes.

**Tech Stack:** Kotlin, Android, Room, Jetpack Compose, OkHttp

---

## Task 1: Circuit Breaker — Stop After 3 Consecutive Tool Failures

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt:312-332`

**Step 1: Read current executeTool method**

```bash
# Read AgentLoopExecutor.kt around line 312
```

**Step 2: Add circuit breaker state**

Add to `AgentLoopExecutor`:
```kotlin
private var consecutiveFailures = 0
private val CIRCUIT_BREAKER_THRESHOLD = 3

private fun recordFailure() {
    consecutiveFailures++
}

private fun recordSuccess() {
    consecutiveFailures = 0
}

private fun isCircuitOpen(): Boolean {
    return consecutiveFailures >= CIRCUIT_BREAKER_THRESHOLD
}
```

**Step 3: Modify executeTool to record failures**

In `executeTool`, after a tool returns an error result, call `recordFailure()`. After a successful execution, call `recordSuccess()`.

The error result from a tool looks like:
```kotlin
return ToolResult.error(toolName, lastError ?: "Execution failed after $maxRetries retries", ...)
```

We need to detect this. The `ToolResult` has `success: Boolean` field.

**Step 4: Check circuit before executing tool**

At the start of `executeTool`:
```kotlin
if (isCircuitOpen()) {
    return ToolResult.error(toolName, "Circuit breaker open — tool disabled after $CIRCUIT_BREAKER_THRESHOLD consecutive failures", "Stop the current task and try a different approach.")
}
```

**Step 5: Reset on completion**

In `startLoop` at the top (when starting a new task):
```kotlin
consecutiveFailures = 0
```

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: add circuit breaker — stop after 3 consecutive tool failures"
```

---

## Task 2: Screenshot Compression — Cap Image Dimensions at 1080p

**Files:**
- Modify: `app/src/main/java/com/phoneagent/perception/VisionPayloadBuilder.kt`

**Step 1: Read VisionPayloadBuilder.kt**

```bash
# Read VisionPayloadBuilder.kt
```

**Step 2: Add dimension cap utility**

```kotlin
private fun Bitmap.scaleToMaxDimension(maxDim: Int = 1080): Bitmap {
    if (width <= maxDim && height <= maxDim) return this
    val scale = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
    return Bitmap.createScaledBitmap(
        (width * scale).toInt(),
        (height * scale).toInt(),
        true
    )
}
```

**Step 3: Modify encodeImage to compress and cap**

Current encodeImage is likely:
```kotlin
fun encodeImage(bytes: ByteArray): String {
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}
```

We need to decode, scale, re-encode as JPEG with quality setting:

```kotlin
fun encodeImage(bytes: ByteArray, maxDim: Int = 1080, quality: Int = 85): String {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val scaled = bitmap.scaleToMaxDimension(maxDim)
    val outputStream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    scaled.recycle()
    bitmap.recycle()
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}
```

Note: We need to add `android.graphics.Bitmap`, `android.graphics.BitmapFactory`, `java.io.ByteArrayOutputStream` imports.

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: cap screenshot dimensions at 1080p JPEG before base64 encoding"
```

---

## Task 3: Jitter on Retry Backoff

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentLoopExecutor.kt:320-328`

**Step 1: Read current retry loop**

```kotlin
// Current:
delay(500L * (1L shl attempt))
```

**Step 2: Add jitter**

```kotlin
private val random = java.util.Random()

private fun retryDelay(attempt: Int, baseMs: Long = 500L): Long {
    val exponential = baseMs * (1L shl attempt)
    val jitter = (exponential * 0.2 * random.nextFloat()).toLong()  // ±20% jitter
    return (exponential + jitter).coerceAtMost(10_000L)  // cap at 10s
}
```

**Step 3: Use in retry loop**

```kotlin
delay(retryDelay(attempt))
```

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: add jitter to tool retry backoff — prevent thundering herd"
```

---

## Task 4: Offline Indicator in UI

**Files:**
- Modify: `app/src/main/java/com/phoneagent/agent/AgentUiState.kt`
- Modify: `app/src/main/java/com/phoneagent/agent/AgentController.kt`
- Modify: `app/src/main/java/com/phoneagent/ui/ChatScreen.kt`

**Step 1: Add isOnline field to AgentUiState**

```kotlin
// In AgentUiState.kt:
data class AgentUiState(
    // ... existing fields
    val isOnline: Boolean = true,
    // ...
)
```

**Step 2: Add connectivity monitoring in AgentController**

In `AgentController`, add:
```kotlin
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network

private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        scope.launch {
            _uiState.update { it.copy(isOnline = true) }
        }
    }
    override fun onLost(network: Network) {
        scope.launch {
            _uiState.update { it.copy(isOnline = false) }
        }
    }
}

// In init block:
val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
connectivityManager.registerDefaultNetworkCallback(connectivityCallback)
```

**Step 3: Display indicator in ChatScreen**

In `ChatScreen.kt`, add a small indicator near the status:

```kotlin
// Where agentStepStatus is shown:
Row {
    if (!uiState.isOnline) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = "Offline",
            tint = Color.Red,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
    }
    Text(uiState.agentStepStatus ?: "", ...)
}
```

Add import for `Icons.Default.WifiOff` (from material icons).

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: add offline indicator to agent UI"
```

---

## Task 5: Expand App Denylist

**Files:**
- Modify: `app/src/main/java/com/phoneagent/accessibility/AppSafetyPolicy.kt`

**Step 1: Read current denylist**

```bash
# Read AppSafetyPolicy.kt
```

**Step 2: Improve the isPackageDenied logic**

Current logic is pure prefix matching. Improve to catch more variants:

```kotlin
fun isPackageDenied(packageName: String?): Boolean {
    if (packageName == null) return false
    val lower = packageName.lowercase()

    // Check exact and prefix matches
    if (denylistPrefixes.any { lower.startsWith(it.lowercase()) }) return true

    // Check for known sensitive app indicators
    val sensitivePatterns = listOf(
        ".mobile.", ".bank.", ".wallet.", ".pay.",
        "authenticator", "2fa", "otp",
        "password", "passkey", "security"
    )
    return sensitivePatterns.any { lower.contains(it) }
}
```

Also update the `getDenyReason` function to return more helpful messages.

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: expand app denylist — catch mobile/wallet/bank suffix variants and security keywords"
```

---

## Task 6: SafetyGate Audit Log to Room

**Files:**
- Create: `app/src/main/java/com/phoneagent/soma/entities/SafetyAuditEntity.kt`
- Create: `app/src/main/java/com/phoneagent/soma/daos/SafetyAuditDao.kt`
- Modify: `app/src/main/java/com/phoneagent/memory/AppDatabase.kt` (add entity + DAO)
- Modify: `app/src/main/java/com/phoneagent/agent/SafetyGate.kt`

**Step 1: Create SafetyAuditEntity**

```kotlin
@Entity(tableName = "safety_audit")
data class SafetyAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolName: String,
    val riskLevel: String,  // "LOW", "MEDIUM", "HIGH"
    val riskReason: String,
    val argsSummary: String,  // truncated args for audit
    val decision: String,  // "APPROVED", "DENIED", "AUTO_ALLOWED"
    val taskId: String?,
    val timestamp: Long = System.currentTimeMillis()
)
```

**Step 2: Create SafetyAuditDao**

```kotlin
@Dao
interface SafetyAuditDao {
    @Insert
    suspend fun insert(audit: SafetyAuditEntity)

    @Query("SELECT * FROM safety_audit ORDER BY timestamp DESC LIMIT 100")
    fun getRecent(): Flow<List<SafetyAuditEntity>>

    @Query("DELETE FROM safety_audit WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
```

**Step 3: Add to AppDatabase**

In `AppDatabase.kt`, add `SafetyAuditEntity::class` to the entities list and `safetyAuditDao(): SafetyAuditDao` abstract function. Add migration for version 4.

**Step 4: Record decisions in SafetyGate or AgentLoopExecutor**

In `AgentLoopExecutor`, when SafetyGate returns MEDIUM/HIGH and user approves/denies, record to DB:

```kotlin
val safetyAuditDao = database.safetyAuditDao()

// When pending confirmation approved:
safetyAuditDao.insert(SafetyAuditEntity(
    toolName = pending.toolName,
    riskLevel = assessment.level.name,
    riskReason = assessment.reason,
    argsSummary = pending.args.toString().take(200),
    decision = "APPROVED",
    taskId = pending.taskId
))

// When denied:
safetyAuditDao.insert(SafetyAuditEntity(
    ...
    decision = "DENIED",
    ...
))
```

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: add SafetyGate audit log to Room — track all dangerous action decisions"
```

---

## Task 7: Final Review and Push

**Step 1: Verify all changes**

```bash
git log --oneline -7
git diff HEAD~1 --stat
```

**Step 2: Push to remote**

```bash
git push
```

---

## Execution Options

**1. Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints

**Which approach?**