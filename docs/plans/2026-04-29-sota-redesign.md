# PhoneAgent SOTA UI/UX Redesign Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign PhoneAgent's entire UI to be state-of-the-art: dark-mode-first, glassmorphism cards, animated message bubbles, modern typography, smooth transitions, and a premium floating overlay experience.

**Architecture:** Replace XML overlay with Compose-based overlay. Introduce a centralized design system (colors, typography, shapes, spacing) as a `PhoneAgentTheme`. Every screen gets redesigned with animated enter/exit transitions, proper visual hierarchy, and micro-interactions.

**Tech Stack:** Jetpack Compose, Material3, Compose Animation APIs (AnimatedVisibility, animateContentSize, animateFloatAsState)

---

### Task 1: Create PhoneAgent Design System (Theme, Colors, Typography, Shapes)

**Files:**
- Create: `app/src/main/java/com/phoneagent/ui/theme/Color.kt`
- Create: `app/src/main/java/com/phoneagent/ui/theme/Type.kt`
- Create: `app/src/main/java/com/phoneagent/ui/theme/Shape.kt`
- Create: `app/src/main/java/com/phoneagent/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/phoneagent/MainActivity.kt`
- Modify: `app/src/main/java/com/phoneagent/PhoneAgentApplication.kt` (if theme needs application-wide setup)
- Modify: `app/src/main/res/values/themes.xml`

**Design specs:**
- **Background:** Deep charcoal `#0D0D0F` (not pure black)
- **Surface:** Slightly lighter `#1A1A1E` with 1dp border `#2A2A2E`
- **Primary:** Electric violet `#8B5CF6` (vibrant, AI-associated)
- **Primary container:** `#8B5CF6` at 15% opacity on surface
- **Secondary:** Cyan `#06B6D4` (for accents, status)
- **Error:** Soft red `#EF4444`
- **Success:** Emerald `#10B981`
- **Warning:** Amber `#F59E0B`
- **On background:** `#E2E2E5` (soft white, not harsh `#FFFFFF`)
- **On surface:** `#F0F0F3`
- **User message bubble:** Gradient from `#8B5CF620` to `#6366F120` (violet to indigo)
- **Agent message bubble:** `#1E1E24` with `#2A2A32` border
- **Border radius:** 16dp cards, 20dp bubbles, 12dp buttons
- **Typography:** Default Material3 but with tighter line height for chat

**Step 1: Create Color.kt**

```kotlin
package com.phoneagent.ui.theme

import androidx.compose.ui.graphics.Color

// Backgrounds
val Background = Color(0xFF0D0D0F)
val Surface = Color(0xFF1A1A1E)
val SurfaceElevated = Color(0xFF242429)
val SurfaceBorder = Color(0xFF2A2A2E)

// Accents
val Primary = Color(0xFF8B5CF6)
val PrimaryMuted = Color(0x268B5CF6)
val Secondary = Color(0xFF06B6D4)
val SecondaryMuted = Color(0x2606B6D4)

// Semantic
val Success = Color(0xFF10B981)
val Warning = Color(0xFFF59E0B)
val Error = Color(0xFFEF4444)
val Info = Color(0xFF3B82F6)

// Content
val OnBackground = Color(0xFFE2E2E5)
val OnSurface = Color(0xFFF0F0F3)
val OnSurfaceMuted = Color(0xFFA0A0A8)
val OnSurfaceDim = Color(0xFF6B6B74)

// Chat bubbles
val UserBubbleStart = Color(0x408B5CF6)
val UserBubbleEnd = Color(0x406366F1)
val AgentBubble = Color(0xFF1E1E24)
val AgentBubbleBorder = Color(0xFF2A2A32)
```

**Step 2: Create Shape.kt**

```kotlin
package com.phoneagent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PhoneAgentShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

val BubbleShapeUser = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
val BubbleShapeAgent = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
val CardShape = RoundedCornerShape(16.dp)
val ButtonShape = RoundedCornerShape(12.dp)
```

**Step 3: Create Type.kt**

```kotlin
package com.phoneagent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PhoneAgentTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        color = OnBackground
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp,
        color = OnBackground
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = OnBackground
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = OnSurface
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = OnSurface
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = OnSurfaceMuted
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = OnSurfaceMuted
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = OnSurfaceMuted
    )
)
```

**Step 4: Create Theme.kt**

```kotlin
package com.phoneagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnBackground,
    primaryContainer = PrimaryMuted,
    onPrimaryContainer = OnBackground,
    secondary = Secondary,
    onSecondary = OnBackground,
    secondaryContainer = SecondaryMuted,
    onSecondaryContainer = OnBackground,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = OnSurfaceMuted,
    error = Error,
    onError = OnBackground,
    outline = SurfaceBorder,
    outlineVariant = SurfaceBorder.copy(alpha = 0.5f)
)

@Composable
fun PhoneAgentTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PhoneAgentTypography,
        shapes = PhoneAgentShapes,
        content = content
    )
}
```

**Step 5: Update MainActivity.kt to use PhoneAgentTheme**

Replace the `setContent` block:

```kotlin
setContent {
    PhoneAgentTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppRoot(agentController = agentController)
        }
    }
}
```

**Step 6: Update themes.xml for system compatibility**

```xml
<resources>
    <style name="Theme.PhoneAgent" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@android:color/black</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

**Step 7: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 2: Redesign ChatScreen with Message Bubbles and Animations

**Files:**
- Modify: `app/src/main/java/com/phoneagent/ui/ChatScreen.kt`
- Create: `app/src/main/java/com/phoneagent/ui/components/MessageBubble.kt`
- Create: `app/src/main/java/com/phoneagent/ui/components/TypingIndicator.kt`
- Create: `app/src/main/java/com/phoneagent/ui/components/ConfirmationDialog.kt`
- Create: `app/src/main/java/com/phoneagent/ui/components/StatusChip.kt`

**Step 1: Create MessageBubble.kt**

```kotlin
package com.phoneagent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.ChatMessage
import com.phoneagent.ui.theme.*

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isUser) {
        Brush.linearGradient(listOf(UserBubbleStart, UserBubbleEnd))
    } else null
    val textColor = if (isUser) OnBackground else OnSurface
    val shape = if (isUser) BubbleShapeUser else BubbleShapeAgent

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically { it / 2 }
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(shape)
                    .then(
                        if (bubbleColor != null) {
                            Modifier.background(bubbleColor)
                        } else {
                            Modifier
                                .background(AgentBubble)
                                .padding(1.dp)
                                .background(AgentBubble, shape)
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        }
    }
}
```

**Step 2: Create TypingIndicator.kt**

```kotlin
package com.phoneagent.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.phoneagent.ui.theme.AgentBubble
import com.phoneagent.ui.theme.Primary

@Composable
fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val delays = listOf(0, 150, 300)

    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(AgentBubble, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            delays.forEach { delay ->
                val animatedValue by transition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot$delay"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(animatedValue)
                        .alpha(animatedValue)
                        .background(Primary, CircleShape)
                )
            }
        }
    }
}
```

**Step 3: Create ConfirmationDialog.kt**

```kotlin
package com.phoneagent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.PendingConfirmation
import com.phoneagent.ui.theme.*

@Composable
fun ConfirmationDialog(
    pending: PendingConfirmation,
    onApprove: () -> Unit,
    onCancel: () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(initialScale = 0.9f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .clip(CardShape)
                    .background(Surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Warning.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Warning,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Confirm Action",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The agent wants to perform a sensitive action:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceMuted
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Action card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .padding(16.dp)
                ) {
                    Row {
                        Text(
                            text = "Tool: ",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceMuted
                        )
                        Text(
                            text = pending.toolName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (pending.args.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        pending.args.forEach { (key, value) ->
                            Row {
                                Text(
                                    text = "$key: ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OnSurfaceMuted
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurface,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = pending.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = ButtonShape
                    ) {
                        Text("Deny")
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                brush = Brush.horizontalGradient(listOf(Primary, Secondary)),
                                shape = ButtonShape
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = ButtonShape
                    ) {
                        Text("Approve")
                    }
                }
            }
        }
    }
}
```

**Step 4: Create StatusChip.kt**

```kotlin
package com.phoneagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.phoneagent.ui.theme.*

@Composable
fun StatusChip(
    text: String,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isActive) Success.copy(alpha = 0.15f) else Error.copy(alpha = 0.15f)
    val textColor = if (isActive) Success else Error
    val dotColor = if (isActive) Success else Error

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, RoundedCornerShape(50))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}
```

**Step 5: Rewrite ChatScreen.kt**

```kotlin
package com.phoneagent.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentAction
import com.phoneagent.agent.AgentController
import com.phoneagent.agent.AgentStep
import com.phoneagent.agent.ChatMessage
import com.phoneagent.agent.ToolResultParser
import com.phoneagent.ui.components.*
import com.phoneagent.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    agentController: AgentController,
    onBack: () -> Unit
) {
    val uiState by agentController.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showSteps by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    // Confirmation dialog
    uiState.pendingConfirmation?.let { pending ->
        ConfirmationDialog(
            pending = pending,
            onApprove = { agentController.approvePendingAction() },
            onCancel = { agentController.cancelPendingAction() }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PhoneAgent", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = uiState.currentModel.takeIf { it.isNotBlank() } ?: "No model",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { agentController.clearChat() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear chat", tint = OnSurfaceMuted)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background.copy(alpha = 0.9f),
                    titleContentColor = OnBackground
                )
            )
        },
        bottomBar = {
            Column {
                // Step viewer (collapsible)
                AnimatedVisibility(
                    visible = uiState.currentSteps.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    StepViewer(
                        steps = uiState.currentSteps,
                        showSteps = showSteps,
                        onToggle = { showSteps = !showSteps }
                    )
                }

                // Error banner
                AnimatedVisibility(
                    visible = uiState.error != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    uiState.error?.let { error ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Error.copy(alpha = 0.1f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = Error
                            )
                        }
                    }
                }

                // Input bar
                ChatInputBar(
                    value = messageText,
                    onValueChange = { messageText = it },
                    onSend = {
                        if (messageText.isNotBlank()) {
                            agentController.sendMessage(messageText)
                            messageText = ""
                            keyboardController?.hide()
                        }
                    },
                    onVoice = { agentController.startVoiceInput() },
                    isLoading = uiState.isLoading,
                    hasPendingConfirmation = uiState.pendingConfirmation != null
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)
            ) {
                items(uiState.messages, key = { it.hashCode() }) { msg ->
                    MessageBubble(message = msg)
                }

                if (uiState.isLoading && uiState.messages.isNotEmpty()) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            // Empty state
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                EmptyChatState()
            }

            // Loading overlay for first message
            if (uiState.isLoading && uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.agentStepStatus ?: "Initializing...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    isLoading: Boolean,
    hasPendingConfirmation: Boolean
) {
    val enabled = !isLoading && !hasPendingConfirmation

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Ask PhoneAgent...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceDim
                    )
                },
                enabled = enabled,
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = SurfaceBorder,
                    disabledBorderColor = SurfaceBorder.copy(alpha = 0.3f),
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    disabledContainerColor = Surface.copy(alpha = 0.5f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                trailingIcon = {
                    IconButton(
                        onClick = onVoice,
                        enabled = enabled
                    ) {
                        Icon(
                            Icons.Default.KeyboardVoice,
                            contentDescription = "Voice input",
                            tint = if (enabled) Secondary else OnSurfaceDim
                        )
                    }
                }
            )

            val sendGradient = Brush.linearGradient(listOf(Primary, Secondary))
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && enabled,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (value.isNotBlank() && enabled) sendGradient else SurfaceBorder.copy(alpha = 0.3f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (value.isNotBlank() && enabled) OnBackground else OnSurfaceDim,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyChatState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PA",
                style = MaterialTheme.typography.headlineLarge,
                color = Primary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "PhoneAgent",
            style = MaterialTheme.typography.titleLarge,
            color = OnBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your AI assistant for Android",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMuted
        )
    }
}

@Composable
private fun StepViewer(
    steps: List<AgentStep>,
    showSteps: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = CardShape
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showSteps) "Hide steps" else "Show steps (${steps.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary
                    )
                }
                Text(
                    text = if (showSteps) "v" else ">",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceMuted
                )
            }

            AnimatedVisibility(visible = showSteps) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    steps.forEach { step ->
                        StepRow(step = step)
                        if (step != steps.last()) {
                            Divider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = SurfaceBorder
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: AgentStep) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        when (step.action) {
            is AgentAction.ToolCall -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when {
                        step.observation?.contains("\"success\":true") == true -> Success
                        step.observation?.contains("\"success\":false") == true -> Error
                        else -> Warning
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step.action.tool,
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurface
                    )
                }
            }
            is AgentAction.FinalAnswer -> {
                Text(
                    text = "Completed",
                    style = MaterialTheme.typography.labelMedium,
                    color = Success
                )
            }
            is AgentAction.ParseError -> {
                Text(
                    text = "Parse error",
                    style = MaterialTheme.typography.labelMedium,
                    color = Error
                )
            }
        }
    }
}
```

**Step 6: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 3: Redesign MainScreen as Dashboard

**Files:**
- Modify: `app/src/main/java/com/phoneagent/ui/MainScreen.kt`

**Step 1: Rewrite MainScreen.kt**

```kotlin
package com.phoneagent.ui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentController
import com.phoneagent.browser.AgentBrowserActivity
import com.phoneagent.overlay.OverlayService
import com.phoneagent.ui.components.StatusChip
import com.phoneagent.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    agentController: AgentController,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current
    var overlayRunning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PhoneAgent", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "AI Agent for Android",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = OnBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // Hero card
                HeroCard(
                    isRunning = overlayRunning,
                    onStart = {
                        OverlayService.start(context)
                        overlayRunning = true
                    },
                    onStop = {
                        OverlayService.stop(context)
                        overlayRunning = false
                    }
                )
            }

            item {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(4) { index ->
                val actions = listOf(
                    ActionCardData(
                        icon = Icons.Default.Chat,
                        title = "Open Chat",
                        subtitle = "Full-screen conversation",
                        gradient = listOf(Primary, Secondary),
                        onClick = onNavigateToChat
                    ),
                    ActionCardData(
                        icon = Icons.Default.Language,
                        title = "Agent Browser",
                        subtitle = "Built-in web browser",
                        gradient = listOf(Secondary, Info),
                        onClick = {
                            context.startActivity(Intent(context, AgentBrowserActivity::class.java))
                        }
                    ),
                    ActionCardData(
                        icon = Icons.Default.Settings,
                        title = "Provider Settings",
                        subtitle = "API keys & models",
                        gradient = null,
                        onClick = onNavigateToSettings
                    ),
                    ActionCardData(
                        icon = Icons.Default.Security,
                        title = "Permissions",
                        subtitle = "Manage access",
                        gradient = null,
                        onClick = onNavigateToPermissions
                    )
                )
                ActionCard(data = actions[index])
            }
        }
    }
}

@Composable
private fun HeroCard(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val gradient = Brush.linearGradient(listOf(Primary.copy(alpha = 0.3f), Secondary.copy(alpha = 0.2f)))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(gradient)
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Overlay Service",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusChip(
                        text = if (isRunning) "Running" else "Stopped",
                        isActive = isRunning
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isRunning) Success.copy(alpha = 0.2f) else SurfaceBorder,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isRunning) Success else OnSurfaceDim,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = if (isRunning) onStop else onStart,
                modifier = Modifier.fillMaxWidth(),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Error.copy(alpha = 0.2f) else Primary
                )
            ) {
                Text(
                    text = if (isRunning) "Stop Service" else "Start Service",
                    color = if (isRunning) Error else OnBackground
                )
            }
        }
    }
}

data class ActionCardData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val gradient: List<androidx.compose.ui.graphics.Color>?,
    val onClick: () -> Unit
)

@Composable
private fun ActionCard(data: ActionCardData) {
    val backgroundModifier = if (data.gradient != null) {
        Modifier.background(
            Brush.linearGradient(data.gradient.map { it.copy(alpha = 0.15f) }),
            CardShape
        )
    } else {
        Modifier.background(Surface, CardShape)
    }

    Card(
        onClick = data.onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = CardShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(backgroundModifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (data.gradient != null) data.gradient[0].copy(alpha = 0.2f) else SurfaceElevated,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = if (data.gradient != null) data.gradient[0] else Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnBackground,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = data.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = OnSurfaceDim,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

**Step 2: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 4: Redesign Overlay Chat (Replace XML with Compose)

**Files:**
- Modify: `app/src/main/java/com/phoneagent/overlay/ChatOverlayController.kt`
- Create: `app/src/main/java/com/phoneagent/overlay/OverlayChatComposeView.kt`
- Modify: `app/src/main/java/com/phoneagent/overlay/FloatingBubbleController.kt`
- Modify: `app/src/main/res/layout/overlay_chat.xml` (delete or keep as fallback)

**Step 1: Create OverlayChatComposeView.kt**

```kotlin
package com.phoneagent.overlay

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentController
import com.phoneagent.agent.ChatMessage
import com.phoneagent.ui.components.MessageBubble
import com.phoneagent.ui.theme.*

class OverlayChatComposeView(context: Context) : ComposeView(context) {

    init {
        setContent {
            PhoneAgentTheme {
                // Content set externally via setter
            }
        }
    }
}

@Composable
fun OverlayChatContent(
    agentController: AgentController,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val uiState by agentController.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Surface.copy(alpha = 0.98f),
                            SurfaceElevated.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Primary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PA",
                            style = MaterialTheme.typography.labelMedium,
                            color = Primary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PhoneAgent",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnBackground,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        Text(
                            text = uiState.currentModel.takeIf { it.isNotBlank() } ?: "Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row {
                    IconButton(onClick = onMinimize, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Minimize,
                            contentDescription = "Minimize",
                            tint = OnSurfaceDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = OnSurfaceDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Messages
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Background.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                if (uiState.messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tap the mic or type to start",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.messages) { msg ->
                            MessageBubble(message = msg)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status
            if (uiState.isLoading) {
                Text(
                    text = uiState.agentStepStatus ?: "Thinking...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = Error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Input
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Ask...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceDim
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary.copy(alpha = 0.4f),
                        unfocusedBorderColor = SurfaceBorder,
                        focusedContainerColor = Background.copy(alpha = 0.5f),
                        unfocusedContainerColor = Background.copy(alpha = 0.5f)
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
                    enabled = !uiState.isLoading
                )

                IconButton(
                    onClick = { agentController.startVoiceInput() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Secondary.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.KeyboardVoice,
                        contentDescription = "Voice",
                        tint = Secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                val sendGradient = Brush.linearGradient(listOf(Primary, Secondary))
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            agentController.sendMessage(messageText)
                            messageText = ""
                        }
                    },
                    enabled = messageText.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (messageText.isNotBlank() && !uiState.isLoading) sendGradient else SurfaceBorder.copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (messageText.isNotBlank() && !uiState.isLoading) OnBackground else OnSurfaceDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
```

**Step 2: Rewrite ChatOverlayController.kt to use Compose overlay**

```kotlin
package com.phoneagent.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ChatOverlayController(
    private val context: Context,
    private val viewModel: com.phoneagent.agent.AgentController
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var chatView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun show() {
        if (chatView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            dimAmount = 0.4f
            y = 24
        }

        params = layoutParams

        val composeView = ComposeView(context).apply {
            setContent {
                com.phoneagent.ui.theme.PhoneAgentTheme {
                    OverlayChatContent(
                        agentController = viewModel,
                        onMinimize = { hide() },
                        onClose = {
                            hide()
                            // Optional: stop service on close
                        }
                    )
                }
            }
        }

        chatView = composeView
        windowManager.addView(composeView, layoutParams)
    }

    fun hide() {
        chatView?.let {
            runCatching { windowManager.removeView(it) }
            chatView = null
        }
    }

    fun isShowing(): Boolean = chatView != null

    fun destroy() {
        hide()
        scope.cancel()
    }
}
```

**Step 3: Update FloatingBubbleController.kt for premium look**

Check the existing file first, then add glow/gradient effect:

```kotlin
// In FloatingBubbleController.kt, update the bubble view to use a gradient circle
// with a subtle glow shadow
```

Since we can't see the exact current implementation, the plan should describe: add a gradient circular background with `Primary` to `Secondary` gradient, add a subtle shadow/elevation, and animate on touch.

**Step 4: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 5: Redesign PermissionScreen and OnboardingScreen

**Files:**
- Modify: `app/src/main/java/com/phoneagent/ui/PermissionScreen.kt`
- Modify: `app/src/main/java/com/phoneagent/ui/OnboardingScreen.kt`

**Step 1: Rewrite PermissionScreen.kt**

Use cards with icons, StatusChip for granted/not granted, and animated transitions. Replace the plain text list with a card-based layout.

Key changes:
- Each permission as a card with an icon
- StatusChip for status
- Smooth color transitions
- Better spacing

**Step 2: Update OnboardingScreen.kt**

- Add gradient backgrounds per step
- Add icons for each step type
- Better typography hierarchy
- Animated page transitions (slide in/out)

**Step 3: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 6: Add Animations and Polish

**Files:**
- Modify: `app/src/main/java/com/phoneagent/ui/AppRoot.kt`
- Create: `app/src/main/java/com/phoneagent/ui/theme/Animation.kt`

**Step 1: Create Animation.kt**

```kotlin
package com.phoneagent.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*

val PhoneAgentEnterTransition = fadeIn(animationSpec = tween(300)) +
        slideInHorizontally(animationSpec = tween(300)) { it / 4 }

val PhoneAgentExitTransition = fadeOut(animationSpec = tween(300)) +
        slideOutHorizontally(animationSpec = tween(300)) { it / 4 }

val PhoneAgentPopEnterTransition = fadeIn(animationSpec = tween(300)) +
        slideInHorizontally(animationSpec = tween(300)) { -it / 4 }

val PhoneAgentPopExitTransition = fadeOut(animationSpec = tween(300)) +
        slideOutHorizontally(animationSpec = tween(300)) { -it / 4 }
```

**Step 2: Update AppRoot.kt with animated transitions**

```kotlin
composable(
    "settings",
    enterTransition = { PhoneAgentEnterTransition },
    exitTransition = { PhoneAgentExitTransition },
    popEnterTransition = { PhoneAgentPopEnterTransition },
    popExitTransition = { PhoneAgentPopExitTransition }
) {
    ProviderSettingsScreen(...)
}
```

Apply to all routes.

**Step 3: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 7: Final Verification

**Step 1: Check all imports resolve**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 2: Verify no XML overlay dependencies remain**

Check that `ChatOverlayController` no longer inflates `R.layout.overlay_chat`.

**Step 3: Run existing tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All 28 tests pass (10 existing + 12 SafetyGate + 6 ToolRegistry)

---

## Summary

| Task | Component | Key Changes |
|------|-----------|-------------|
| 1 | Design System | Dark theme, custom colors, typography, shapes |
| 2 | ChatScreen | Message bubbles, typing indicator, confirmation dialog, empty state, input bar |
| 3 | MainScreen | Dashboard cards, hero gradient, action cards with icons |
| 4 | Overlay | Compose-based overlay chat, gradient bubble, dim background |
| 5 | Permissions/Onboarding | Card-based layout, icons, animations |
| 6 | Navigation | Slide transitions between screens |
| 7 | Verification | Build passes, tests pass |

**Files created:** 10 new (theme + components)
**Files modified:** 7 existing screens
**Lines added:** ~1,200 (design system + components + screen rewrites)
**Lines removed:** ~400 (old XML layouts, plain text UIs)
