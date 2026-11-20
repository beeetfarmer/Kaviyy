package com.nl.kaviyy.core.model.server

/** Local ID of a configured server; every table is keyed by it (AGENTS.md). */
@JvmInline
value class ServerId(
    val value: Long,
)

/**
 * A configured Kavita server. Credentials are NOT part of this model; they live in the encrypted credential store.
 *
 * @property pinnedCertSha256 TOFU pin (lowercase hex SHA-256 of the leaf certificate), set after the user accepts it.
 * @property allowInsecure user opted in to plain HTTP; only honoured for private hosts
 *   ([ServerUrl.cleartextPermitted]).
 */
data class Server(
    val id: ServerId,
    val name: String,
    val url: ServerUrl,
    val version: String? = null,
    val pinnedCertSha256: String? = null,
    val allowInsecure: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Server name must not be blank" }
        require(
            pinnedCertSha256 == null || SHA256_HEX.matches(pinnedCertSha256),
        ) { "Pin must be 64 lowercase hex chars" }
    }

    val cleartextPermitted: Boolean get() = url.cleartextPermitted(allowInsecure)

    private companion object {
        val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    }
}
