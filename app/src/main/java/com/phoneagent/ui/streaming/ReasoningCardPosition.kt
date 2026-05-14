package com.phoneagent.ui.streaming

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ReasoningCardPosition(
    val x: Float = 0f,
    val y: Float = 200f
)

private val REASONING_CARD_POSITION = stringPreferencesKey("reasoning_card_position")

val Context.reasoningCardDataStore: DataStore<Preferences> by preferencesDataStore(name = "streaming_prefs")

@Composable
fun rememberReasoningCardPosition(
    context: Context
): State<ReasoningCardPosition> {
    val prefs = context.reasoningCardDataStore.data
    return prefs.collectAsState(initial = ReasoningCardPosition()).let { flowState ->
        val current = flowState.value
        remember(current) {
            mutableStateOf(current)
        }
    }
}

suspend fun saveReasoningCardPosition(
    position: ReasoningCardPosition,
    context: Context
) {
    context.reasoningCardDataStore.edit { prefs ->
        prefs[REASONING_CARD_POSITION] = "${position.x},${position.y}"
    }
}