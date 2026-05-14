package com.phoneagent.iris

import com.phoneagent.worldmodel.PersonalWorldModel
import com.phoneagent.worldmodel.RoutingDecisionDao
import com.phoneagent.worldmodel.RoutingDecisionEntity

class IrisRouter(private val routingDecisionDao: RoutingDecisionDao? = null) {

    private val history = mutableListOf<Pair<IrisState, IrisProfile>>()

    data class RoutingDecision(
        val profile: IrisProfile,
        val temperature: Float,
        val style: String,
        val depth: Int,
        val reasoning: String
    )

    fun classifyState(message: String, tensionScore: Float, energyScore: Float): IrisState {
        val msgType = when {
            message.length < 15 -> "command"
            message.contains("?") -> "question"
            message.length > 200 -> "emotional"
            else -> "conversation"
        }
        return IrisState(
            tensionScore = tensionScore,
            energyScore = energyScore,
            messageLength = message.length,
            messageType = msgType
        )
    }

    suspend fun selectProfile(
        state: IrisState,
        worldModel: PersonalWorldModel? = null
    ): RoutingDecision {
        val profile = selectProfileInternal(state)
        val communicationStyle = worldModel?.getProfile()?.communicationStyle ?: "conversational"
        val baseTemp = profile.temperature.toFloat()
        val baseStyle = when (communicationStyle) {
            "concise", "brief" -> "brief"
            "technical" -> "technical"
            "detailed" -> "detailed"
            else -> "conversational"
        }
        val baseDepth = when {
            state.energyScore < 0.3 -> 2
            state.energyScore < 0.6 -> 3
            state.energyScore < 0.8 -> 4
            else -> 5
        }
        val depth = if (state.isLateNight) (baseDepth - 1).coerceAtLeast(1) else baseDepth
        val temperature = (baseTemp + when (communicationStyle) {
            "technical" -> 0.05f
            "detailed" -> 0.1f
            else -> 0f
        }).toFloat().coerceIn(0.1f, 1.2f)

        val reasoning = buildString {
            append("profile=${profile.name}, ")
            append("commStyle=$communicationStyle, ")
            append("energy=${state.energyScore}, ")
            append("lateNight=${state.isLateNight}")
        }

        val decision = RoutingDecision(
            profile = profile,
            temperature = temperature,
            style = baseStyle,
            depth = depth,
            reasoning = reasoning
        )

        recordRoutingDecision(state, decision)

        return decision
    }

    private fun selectProfileInternal(state: IrisState): IrisProfile {
        if (state.messageType == "command" && state.messageLength < 30) {
            return IrisProfile.REFLEX
        }

        if (state.tensionScore > 0.7) {
            return IrisProfile.GENTLE
        }

        if (state.isLateNight) {
            return if (state.energyScore < 0.3) IrisProfile.GENTLE else IrisProfile.DEEP
        }

        if (state.messageType == "question" && state.messageLength < 60) {
            return IrisProfile.FAST
        }

        if (state.messageType == "emotional" || state.tensionScore > 0.5) {
            return IrisProfile.BALANCED
        }

        if (state.messageLength > 150) {
            return IrisProfile.DEEP
        }

        if (state.messageType == "command") {
            return IrisProfile.SHARP
        }

        val historicalBest = learnFromHistory(state)
        return historicalBest ?: IrisProfile.BALANCED
    }

    private fun learnFromHistory(state: IrisState): IrisProfile? {
        if (history.size < 20) return null
        val similarStates = history.filter { (s, _) ->
            similarState(s, state)
        }
        if (similarStates.isEmpty()) return null
        return similarStates.groupBy { it.second }.maxByOrNull { it.value.size }?.key
    }

    fun recordRoutingDecision(state: IrisState, decision: RoutingDecision) {
        history.add(state to decision.profile)
        if (history.size > 300) history.removeAt(0)

        routingDecisionDao?.let { dao ->
            try {
                val entity = RoutingDecisionEntity(
                    messagePreview = state.messageType,
                    profile = decision.profile.name,
                    temperature = decision.temperature,
                    style = decision.style,
                    depth = decision.depth,
                    reasoning = decision.reasoning
                )
                kotlinx.coroutines.runBlocking {
                    dao.insert(entity)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun recordOutcome(outcome: String) {
        routingDecisionDao?.let { dao ->
            try {
                val recent = dao.getRecent(1).first()
                recent.firstOrNull()?.let { entity ->
                    dao.insert(entity.copy(outcome = outcome))
                }
            } catch (_: Exception) {}
        }
    }

    fun recordDecision(state: IrisState, profile: IrisProfile) {
        history.add(state to profile)
        if (history.size > 300) history.removeAt(0)
    }

    private fun similarState(a: IrisState, b: IrisState): Boolean {
        val hourDiff = kotlin.math.abs(a.hour - b.hour)
        return hourDiff <= 3 && a.messageType == b.messageType
    }
}
