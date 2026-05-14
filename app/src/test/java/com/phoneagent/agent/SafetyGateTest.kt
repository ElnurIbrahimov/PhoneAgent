package com.phoneagent.agent

import com.phoneagent.agent.SafetyGate.RiskLevel
import com.phoneagent.worldmodel.PersonalWorldModel
import com.phoneagent.worldmodel.PreferenceEntity
import com.phoneagent.worldmodel.PersonalProfileEntity
import com.phoneagent.soma.BeliefEngine
import com.phoneagent.soma.daos.BeliefDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.anyString

class SafetyGateTest {

    private lateinit var mockWorldModel: PersonalWorldModel
    private lateinit var mockProfile: PersonalProfileEntity

    @Before
    fun setup() {
        mockWorldModel = mock(PersonalWorldModel::class.java)
        mockProfile = PersonalProfileEntity(
            id = "self",
            communicationStyle = "CASUAL",
            riskTolerance = "MEDIUM",
            specialRequirements = "[]",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    @Test
    fun `low risk tolerance escalates MEDIUM to HIGH`() = runBlocking {
        `when`(mockWorldModel.getProfile()).thenReturn(mockProfile.copy(riskTolerance = "LOW"))
        `when`(mockWorldModel.getPreferencesForCategory("safety_denied")).thenReturn(emptyList())
        `when`(mockWorldModel.getBelief(anyString(), anyString())).thenReturn(0.0)

        val result = SafetyGate.assess(
            "browser.click_text",
            mapOf("text" to "Submit"),
            mockWorldModel
        )

        assertEquals(RiskLevel.HIGH, result.level)
        assert(result.escalationFactors.isNotEmpty())
        assert(result.escalationFactors.any { it.contains("LOW risk tolerance") })
    }

    @Test
    fun `previously denied tool escalates to HIGH`() = runBlocking {
        `when`(mockWorldModel.getProfile()).thenReturn(mockProfile.copy(riskTolerance = "MEDIUM"))

        val deniedPrefs = listOf(
            PreferenceEntity(category = "safety_denied", key = "browser.click_text", value = "true", confidence = 1f)
        )
        `when`(mockWorldModel.getPreferencesForCategory("safety_denied")).thenReturn(deniedPrefs)
        `when`(mockWorldModel.getBelief(anyString(), anyString())).thenReturn(0.0)

        val result = SafetyGate.assess(
            "browser.click_text",
            mapOf("text" to "OK"),
            mockWorldModel
        )

        assertEquals(RiskLevel.HIGH, result.level)
        assert(result.escalationFactors.any { it.contains("previously denied") })
    }

    @Test
    fun `high privacy concern escalates privacy-sensitive tool`() = runBlocking {
        `when`(mockWorldModel.getProfile()).thenReturn(mockProfile.copy(riskTolerance = "MEDIUM"))
        `when`(mockWorldModel.getPreferencesForCategory("safety_denied")).thenReturn(emptyList())
        `when`(mockWorldModel.getBelief("privacy", "concerned")).thenReturn(0.8)

        val result = SafetyGate.assess(
            "phone.clipboard",
            mapOf("action" to "read"),
            mockWorldModel
        )

        assertEquals(RiskLevel.HIGH, result.level)
        assert(result.escalationFactors.any { it.contains("privacy concern") })
    }

    @Test
    fun `world model with null profile uses default MEDIUM tolerance`() = runBlocking {
        `when`(mockWorldModel.getProfile()).thenReturn(null)
        `when`(mockWorldModel.getPreferencesForCategory("safety_denied")).thenReturn(emptyList())
        `when`(mockWorldModel.getBelief(anyString(), anyString())).thenReturn(0.0)

        val result = SafetyGate.assess(
            "browser.click_text",
            mapOf("text" to "Confirm"),
            mockWorldModel
        )

        assertEquals(RiskLevel.MEDIUM, result.level)
        assert(result.escalationFactors.isEmpty())
    }

    @Test
    fun `no world model returns base assessment without escalation`() = runBlocking {
        val result = SafetyGate.assess(
            "browser.click_text",
            mapOf("text" to "Submit"),
            null
        )

        assertEquals(RiskLevel.MEDIUM, result.level)
        assert(result.escalationFactors.isEmpty())
    }

    @Test
    fun `multiple escalation factors all appear in escalation list`() = runBlocking {
        `when`(mockWorldModel.getProfile()).thenReturn(mockProfile.copy(riskTolerance = "LOW"))

        val deniedPrefs = listOf(
            PreferenceEntity(category = "safety_denied", key = "browser.click_text", value = "true", confidence = 1f)
        )
        `when`(mockWorldModel.getPreferencesForCategory("safety_denied")).thenReturn(deniedPrefs)
        `when`(mockWorldModel.getBelief("privacy", "concerned")).thenReturn(0.85)

        val result = SafetyGate.assess(
            "browser.click_text",
            mapOf("text" to "Submit"),
            mockWorldModel
        )

        assertEquals(RiskLevel.HIGH, result.level)
        assert(result.escalationFactors.size >= 2)
    }
}