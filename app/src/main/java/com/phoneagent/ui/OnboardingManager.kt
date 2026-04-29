package com.phoneagent.ui

import android.content.Context
import android.content.SharedPreferences

object OnboardingManager {
    private const val PREFS_NAME = "phoneagent_onboarding"
    private const val KEY_COMPLETED = "onboarding_completed"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMPLETED, false)

    fun markCompleted(context: Context) {
        prefs(context).edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().putBoolean(KEY_COMPLETED, false).apply()
    }
}
