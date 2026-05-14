package com.phoneagent.worldmodel

enum class ScreenType {
    HOMESCREEN,
    APP_LAUNCHER,
    LIST,
    MESSAGING,
    CALENDAR,
    EMAIL,
    BROWSER,
    SETTINGS,
    FINANCIAL,
    SOCIAL_MEDIA,
    UNKNOWN
}

data class UIElement(
    val text: String,
    val bounds: android.graphics.Rect,
    val isInteractive: Boolean,
    val isSensitive: Boolean,
    val role: String
)

data class AppContext(
    val packageName: String,
    val appName: String,
    val category: String,
    val trustLevel: String
)

data class ScreenUnderstanding(
    val screenType: ScreenType,
    val mainPurpose: String,
    val confidence: Float,
    val interactiveElements: List<UIElement>
)

data class PhoneStateModel(
    val foregroundApp: String,
    val appHierarchy: String,
    val currentScreen: ScreenUnderstanding?,
    val recentActions: List<String>
)