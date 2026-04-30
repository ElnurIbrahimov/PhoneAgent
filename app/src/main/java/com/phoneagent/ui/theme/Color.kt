package com.phoneagent.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0D0D0F)
val Surface = Color(0xFF1A1A1E)
val SurfaceElevated = Color(0xFF242429)
val SurfaceBorder = Color(0xFF2A2A2E)

val Primary = Color(0xFF8B5CF6)
val PrimaryMuted = Color(0x268B5CF6)
val Secondary = Color(0xFF06B6D4)
val SecondaryMuted = Color(0x2606B6D4)

val Success = Color(0xFF10B981)
val Warning = Color(0xFFF59E0B)
val Error = Color(0xFFEF4444)
val Info = Color(0xFF3B82F6)

val OnBackground = Color(0xFFE2E2E5)
val OnSurface = Color(0xFFF0F0F3)
val OnSurfaceMuted = Color(0xFFA0A0A8)
val OnSurfaceDim = Color(0xFF8E8E99)

val UserBubbleStart = Color(0x408B5CF6)
val UserBubbleEnd = Color(0x406366F1)
val AgentBubble = Color(0xFF1E1E24)
val AgentBubbleBorder = Color(0xFF2A2A32)

val AccentGradient = Brush.linearGradient(listOf(Primary, Secondary))
val AccentGradientMuted = Brush.linearGradient(listOf(Primary.copy(alpha = 0.15f), Secondary.copy(alpha = 0.15f)))
