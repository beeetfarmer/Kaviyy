package com.nl.kaviyy.core.model.server

import java.security.MessageDigest
import java.util.Base64

/** Local ID of a configured server; every table is keyed by it (AGENTS.md). */
@JvmInline
value class ServerId(
    val value: Long,
)

/**
 * A configured Kavita server. Credentials are NOT part of this model; they live in the encrypted credential store.
 *
 * @property pinnedCertificate TOFU-accepted certificate (N3), set after the user accepts it on the trust sheet.
 * @property allowInsecure user opted in to plain HTTP; only honoured for private hosts
 *   ([ServerUrl.cleartextPermitted]).
 * @property username the signed-in Kavita user (display only).
 * @property roles the user's server roles from sign-in, e.g. "Download" gates offline downloads.
 */
data class Server(
    val id: ServerId,
    val name: String,
    val url: ServerUrl,
    val version: String? = null,
    val pinnedCertificate: PinnedCertificate? = null,
    val allowInsecure: Boolean = false,
    val username: String? = null,
    val roles: Set<String> = emptySet(),
) {
    init {
        require(name.isNotBlank()) { "Server name must not be blank" }
    }

    val cleartextPermitted: Boolean get() = url.cleartextPermitted(allowInsecure)
}

/** A certificate the user accepted (DER, base64). [sha256] is the lowercase hex fingerprint for the trust sheet. */
@JvmInline
value class PinnedCertificate(
    val derBase64: String,
) {
    init {
        require(derBase64.isNotEmpty() && runCatching { Base64.getDecoder().decode(derBase64) }.isSuccess) {
            "Pinned certificate must be base64 DER"
        }
    }

    val der: ByteArray get() = Base64.getDecoder().decode(derBase64)

    val sha256: String get() = sha256Hex(der)

    companion object {
        fun of(der: ByteArray) = PinnedCertificate(Base64.getEncoder().encodeToString(der))

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
