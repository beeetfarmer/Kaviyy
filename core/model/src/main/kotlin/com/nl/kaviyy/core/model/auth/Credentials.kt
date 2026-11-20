package com.nl.kaviyy.core.model.auth

/** Secrets for one server. `toString` never prints secret values (they end up in logs and crash reports). */
sealed interface Credentials {
    /** The key Kavita accepts as the `apiKey` query parameter on image endpoints, if known. */
    val imageApiKey: String?
        get() =
            when (this) {
                is Jwt -> apiKey
                is AuthKey -> apiKey
            }

    /**
     * Username/password login result. The password itself is never stored.
     * [apiKey] comes from the login response (UserDto.apiKey); image URLs need it (see core:network Auth.kt).
     */
    data class Jwt(
        val username: String,
        val token: String,
        val refreshToken: String,
        val apiKey: String? = null,
    ) : Credentials {
        override fun toString() =
            "Jwt(username=$username, token=<redacted>, refreshToken=<redacted>, apiKey=<redacted>)"
    }

    /** Kavita Auth Key (`x-api-key`), used by SSO users. */
    data class AuthKey(
        val apiKey: String,
    ) : Credentials {
        override fun toString() = "AuthKey(apiKey=<redacted>)"
    }
}
