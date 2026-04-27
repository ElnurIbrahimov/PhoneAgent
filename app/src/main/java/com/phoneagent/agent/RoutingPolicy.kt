package com.phoneagent.agent

data class RoutingPolicy(
    val fallbackToDefault: Boolean = true,
    val preferLocal: Boolean = false,
    val maxRetries: Int = 2
)
