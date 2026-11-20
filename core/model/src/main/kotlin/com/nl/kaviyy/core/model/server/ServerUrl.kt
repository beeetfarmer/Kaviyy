package com.nl.kaviyy.core.model.server

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException

/** Why a typed server address was rejected (shown inline on the Connect screen). */
enum class ServerUrlError {
    Empty,
    UnsupportedScheme,
    ContainsCredentials,
    ContainsQueryOrFragment,
    InvalidHost,
    InvalidPort,
    Malformed,
}

sealed interface ServerUrlResult {
    data class Valid(
        val url: ServerUrl,
    ) : ServerUrlResult

    data class Invalid(
        val error: ServerUrlError,
    ) : ServerUrlResult
}

/**
 * A normalised Kavita base URL: http(s) only, lowercase scheme + ASCII host, no credentials, query or fragment,
 * no trailing slash or trailing `/api`. Build it with [ServerUrl.parse]. Non-ASCII (IDN) hosts are rejected.
 */
@ConsistentCopyVisibility
data class ServerUrl private constructor(
    val scheme: String,
    val host: String,
    val port: Int?,
    val path: String,
) {
    val isHttps: Boolean get() = scheme == HTTPS

    /** `https://host[:port][/path]` with no trailing slash. */
    val value: String
        get() =
            buildString {
                append(scheme).append("://")
                append(if (':' in host) "[$host]" else host)
                if (port != null) append(':').append(port)
                append(path)
            }

    /** Endpoint URL under this base, e.g. `resolve("api/Server/server-info")`. */
    fun resolve(relative: String): String = "$value/${relative.trimStart('/')}"

    /** True for LAN-only hosts (SECURITY_CHECKLIST N2). Literal check only: never trusts DNS. */
    val isPrivateHost: Boolean get() = PrivateHosts.isPrivate(host)

    /** N2: plain HTTP only after the user opted in for this server AND the host is on a private network. */
    fun cleartextPermitted(allowInsecure: Boolean): Boolean = isHttps || (allowInsecure && isPrivateHost)

    override fun toString(): String = value

    companion object {
        private const val HTTP = "http"
        private const val HTTPS = "https"
        private const val MAX_PORT = 65535
        private val DEFAULT_PORTS = mapOf(HTTP to 80, HTTPS to 443)

        fun parse(input: String): ServerUrlResult =
            try {
                ServerUrlResult.Valid(build(input.trim()))
            } catch (e: InvalidServerUrl) {
                ServerUrlResult.Invalid(e.error)
            }

        private class InvalidServerUrl(
            val error: ServerUrlError,
        ) : IllegalArgumentException(error.name)

        private fun reject(error: ServerUrlError): Nothing = throw InvalidServerUrl(error)

        private fun build(input: String): ServerUrl {
            if (input.isEmpty()) reject(ServerUrlError.Empty)
            val uri =
                try {
                    URI(if ("://" in input) input else "$HTTPS://$input")
                } catch (_: URISyntaxException) {
                    reject(ServerUrlError.Malformed)
                }
            validate(uri)?.let(::reject)
            val scheme = uri.scheme.lowercase()
            val host = normaliseHost(uri.host) ?: reject(ServerUrlError.InvalidHost)
            val port = uri.port.takeIf { it != -1 && it != DEFAULT_PORTS[scheme] }
            return ServerUrl(scheme, host, port, normalisePath(uri.rawPath.orEmpty()))
        }

        private fun validate(uri: URI): ServerUrlError? =
            when {
                uri.scheme?.lowercase() !in DEFAULT_PORTS -> ServerUrlError.UnsupportedScheme
                uri.rawUserInfo != null || uri.rawAuthority?.contains('@') == true -> ServerUrlError.ContainsCredentials
                uri.rawQuery != null || uri.rawFragment != null -> ServerUrlError.ContainsQueryOrFragment
                uri.host.isNullOrBlank() -> ServerUrlError.InvalidHost
                uri.port != -1 && uri.port !in 1..MAX_PORT -> ServerUrlError.InvalidPort
                else -> null
            }

        private fun normaliseHost(raw: String): String? {
            val host =
                raw
                    .removePrefix("[")
                    .removeSuffix("]")
                    .trimEnd('.')
                    .lowercase()
            return when {
                host.isEmpty() -> {
                    null
                }

                ':' in host -> {
                    host.takeIf { PrivateHosts.ipv6(it) != null }
                }

                else -> {
                    runCatching {
                        IDN.toASCII(
                            host,
                            IDN.USE_STD3_ASCII_RULES,
                        )
                    }.getOrNull()?.takeIf { it.isNotEmpty() }
                }
            }
        }

        private fun normalisePath(raw: String): String = raw.trimEnd('/').removeSuffix("/api").removeSuffix("/API")
    }
}

internal object PrivateHosts {
    private val privateSuffixes = listOf(".local", ".lan")
    private val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val ipv6Chars = Regex("^[0-9a-z:.%]+$")

    fun isPrivate(host: String): Boolean =
        privateSuffixes.any { host.endsWith(it) } || (ipv4Private(host) ?: ipv6Private(host))

    /** null when [host] is not an IPv4 literal. */
    @Suppress("MagicNumber")
    private fun ipv4Private(host: String): Boolean? =
        ipv4.matchEntire(host)?.groupValues?.drop(1)?.map { it.toInt() }?.let { o ->
            o.all { it <= 255 } &&
                when (o[0]) {
                    10, 127 -> true

                    // 10/8, loopback
                    172 -> o[1] in 16..31

                    // 172.16/12
                    192 -> o[1] == 168

                    // 192.168/16
                    169 -> o[1] == 254

                    // link-local
                    100 -> o[1] in 64..127

                    // 100.64/10 (Tailscale CGNAT)
                    else -> false
                }
        }

    @Suppress("MagicNumber")
    private fun ipv6Private(host: String): Boolean {
        val v6 = ipv6(host) ?: return false
        // fc00::/7 unique local; fe80::/10 link-local; ::1
        return v6.isLoopbackAddress || v6.isLinkLocalAddress || (v6.address[0].toInt() and 0xFE) == 0xFC
    }

    /** Parses an IPv6 literal without DNS (a host containing ':' is never looked up). */
    fun ipv6(host: String): Inet6Address? =
        if (':' in host && ipv6Chars.matches(host)) {
            runCatching { InetAddress.getByName(host) as? Inet6Address }.getOrNull()
        } else {
            null
        }
}
