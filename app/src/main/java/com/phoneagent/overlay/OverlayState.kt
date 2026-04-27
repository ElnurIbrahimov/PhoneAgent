package com.phoneagent.overlay

sealed class OverlayState {
    object Hidden : OverlayState()
    object BubbleOnly : OverlayState()
    object ChatOpen : OverlayState()
}
