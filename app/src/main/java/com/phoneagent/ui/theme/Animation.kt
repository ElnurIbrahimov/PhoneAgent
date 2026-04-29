package com.phoneagent.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.tween

val PhoneAgentEnterTransition = fadeIn(animationSpec = tween(300)) +
        slideInHorizontally(animationSpec = tween(300)) { it / 4 }

val PhoneAgentExitTransition = fadeOut(animationSpec = tween(300)) +
        slideOutHorizontally(animationSpec = tween(300)) { it / 4 }

val PhoneAgentPopEnterTransition = fadeIn(animationSpec = tween(300)) +
        slideInHorizontally(animationSpec = tween(300)) { -it / 4 }

val PhoneAgentPopExitTransition = fadeOut(animationSpec = tween(300)) +
        slideOutHorizontally(animationSpec = tween(300)) { -it / 4 }