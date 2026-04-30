package com.phoneagent.iris

class IrisRouter {

    private val history = mutableListOf<Pair<IrisState, IrisProfile>>()

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

    fun selectProfile(state: IrisState): IrisProfile {
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

    fun recordDecision(state: IrisState, profile: IrisProfile) {
        history.add(state to profile)
        if (history.size > 300) history.removeAt(0)
    }

    private fun similarState(a: IrisState, b: IrisState): Boolean {
        val hourDiff = kotlin.math.abs(a.hour - b.hour)
        return hourDiff <= 3 && a.messageType == b.messageType
    }
}
