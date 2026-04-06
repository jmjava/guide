package com.embabel.hub.integrations

enum class LlmKeyErrorCode {
    KEY_MISSING,
    KEY_INVALID,
    KEY_QUOTA,
    KEY_EXPIRED,
    PROVIDER_ERROR,
}

/**
 * Thrown when an LLM call fails due to a key or provider auth issue.
 * Caught by the chat handler and forwarded to the client as a structured WS error.
 */
class LlmKeyException(
    val errorCode: LlmKeyErrorCode,
    val provider: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    companion object {

        /**
         * Classifies a raw provider exception into an [LlmKeyException].
         * Returns null if the exception doesn't look key/auth-related.
         */
        fun classify(e: Exception, provider: String): LlmKeyException? {
            val msg = e.message ?: return null
            return when {
                msg.contains("401") || msg.contains("unauthorized", ignoreCase = true) ||
                    msg.contains(Regex("invalid.*key", RegexOption.IGNORE_CASE)) ->
                    LlmKeyException(LlmKeyErrorCode.KEY_INVALID, provider, "Your API key appears to be invalid or expired.", e)

                msg.contains("402") || msg.contains("billing", ignoreCase = true) ||
                    msg.contains("quota", ignoreCase = true) || msg.contains("insufficient", ignoreCase = true) ->
                    LlmKeyException(LlmKeyErrorCode.KEY_QUOTA, provider, "Your API account may have run out of credits.", e)

                msg.contains("429") || msg.contains("rate", ignoreCase = true) ->
                    LlmKeyException(LlmKeyErrorCode.KEY_QUOTA, provider, "The AI provider is rate-limiting requests.", e)

                msg.contains("expired", ignoreCase = true) ->
                    LlmKeyException(LlmKeyErrorCode.KEY_EXPIRED, provider, "Your API key has expired.", e)

                else -> null
            }
        }
    }
}