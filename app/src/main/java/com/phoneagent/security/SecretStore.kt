package com.phoneagent.security

interface SecretStore {
    suspend fun storeSecret(key: String, value: String)
    suspend fun getSecret(key: String): String?
    suspend fun deleteSecret(key: String)
    suspend fun hasSecret(key: String): Boolean
}
