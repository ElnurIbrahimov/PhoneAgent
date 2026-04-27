package com.phoneagent.providers

sealed class ProviderError(message: String) : Exception(message) {
    class NetworkError(message: String) : ProviderError(message)
    class AuthenticationError(message: String) : ProviderError(message)
    class RateLimitError(message: String) : ProviderError(message)
    class InvalidRequestError(message: String) : ProviderError(message)
    class UnknownError(message: String) : ProviderError(message)
    class ServerError(message: String) : ProviderError(message)
}
