package com.ideathon.kondaeshield.settings

import android.content.Context

class ApiKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun getApiKey(provider: ApiProvider): String =
        preferences.getString(provider.apiKeyPreferenceKey(), "").orEmpty()

    fun hasApiKey(provider: ApiProvider): Boolean =
        getApiKey(provider).isNotBlank()

    fun setApiKey(provider: ApiProvider, apiKey: String) {
        preferences.edit()
            .putString(provider.apiKeyPreferenceKey(), apiKey.trim())
            .apply()
    }

    fun clearApiKey(provider: ApiProvider) {
        preferences.edit()
            .remove(provider.apiKeyPreferenceKey())
            .apply()
    }

    fun getStoredProviders(): Set<ApiProvider> =
        ApiProvider.entries.filter(::hasApiKey).toSet()

    fun hasTranscriptionApiKey(): Boolean =
        hasApiKey(ApiProvider.OPENAI) || hasApiKey(ApiProvider.GROQ)

    private fun ApiProvider.apiKeyPreferenceKey(): String =
        "api_key_${name.lowercase()}"

    companion object {
        private const val PREFERENCES_NAME = "nag_blocker_settings"
    }
}
