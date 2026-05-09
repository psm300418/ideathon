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

    fun getTranscriptionProvider(): ApiProvider {
        val saved = preferences.getString(KEY_TRANSCRIPTION_PROVIDER, null)
            ?.let { runCatching { ApiProvider.valueOf(it) }.getOrNull() }
        if (saved != null && saved.supportsTranscription) return saved

        return ApiProvider.entries.first { it.supportsTranscription }
    }

    fun setTranscriptionProvider(provider: ApiProvider) {
        check(provider.supportsTranscription) {
            "${provider.displayName}은 현재 전사 공급자로 사용할 수 없습니다."
        }
        preferences.edit()
            .putString(KEY_TRANSCRIPTION_PROVIDER, provider.name)
            .apply()
    }

    fun hasTranscriptionApiKey(): Boolean =
        hasApiKey(getTranscriptionProvider())

    fun getTranscriptionApiKey(): String =
        getApiKey(getTranscriptionProvider())

    private fun ApiProvider.apiKeyPreferenceKey(): String =
        "api_key_${name.lowercase()}"

    companion object {
        private const val PREFERENCES_NAME = "nag_blocker_settings"
        private const val KEY_TRANSCRIPTION_PROVIDER = "transcription_provider"
    }
}
