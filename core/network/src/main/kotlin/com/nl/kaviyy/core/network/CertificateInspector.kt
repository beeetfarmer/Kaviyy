package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import com.nl.kaviyy.core.model.server.PinnedCertificate
import com.nl.kaviyy.core.model.server.ServerUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.inject.Inject
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** What the trust sheet shows (SECURITY_CHECKLIST N3). */
data class PresentedCertificate(
    val pin: PinnedCertificate,
    val subject: String,
    val issuer: String,
    val notBefore: Date,
    val notAfter: Date,
    /** The certificate names this host. If not, it can't be trusted: hostname verification is never disabled (N4). */
    val matchesHost: Boolean,
) {
    /** `AB:CD:…` form of the SHA-256 fingerprint. */
    val fingerprint: String get() =
        pin.sha256
            .uppercase()
            .chunked(2)
            .joinToString(":")
}

fun interface CertificateFetcher {
    suspend fun fetch(url: ServerUrl): PresentedCertificate?
}

private const val INSPECT_TIMEOUT_MS = 10_000

/**
 * Reads the leaf certificate a server presents, for the TOFU trust sheet. The capturing trust manager records the
 * chain and then *rejects* it, so the handshake always fails: no application data is ever sent over this socket and it
 * can't be used as an HTTP client.
 */
class CertificateInspector
    @Inject
    constructor(
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
    ) : CertificateFetcher {
        override suspend fun fetch(url: ServerUrl): PresentedCertificate? =
            withContext(io) {
                val capture = CapturingTrustManager()
                val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(capture), null) }
                runCatching {
                    (context.socketFactory.createSocket() as SSLSocket).use { socket ->
                        if (!url.host.isIpLiteral()) {
                            socket.sslParameters =
                                socket.sslParameters.apply { serverNames = listOf(SNIHostName(url.host)) }
                        }
                        socket.connect(InetSocketAddress(url.host, url.port ?: HTTPS_PORT), INSPECT_TIMEOUT_MS)
                        socket.soTimeout = INSPECT_TIMEOUT_MS
                        socket.startHandshake()
                    }
                }
                capture.leaf?.let { leaf ->
                    PresentedCertificate(
                        pin = PinnedCertificate.of(leaf.encoded),
                        subject = leaf.subjectX500Principal.name,
                        issuer = leaf.issuerX500Principal.name,
                        notBefore = leaf.notBefore,
                        notAfter = leaf.notAfter,
                        matchesHost = certificateMatchesHost(leaf, url.host),
                    )
                }
            }

        private class CapturingTrustManager : X509TrustManager {
            @Volatile
            var leaf: X509Certificate? = null

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>,
                authType: String,
            ) {
                leaf = chain.firstOrNull()
                throw CertificateException("Inspection only")
            }

            override fun checkClientTrusted(
                chain: Array<out X509Certificate>,
                authType: String,
            ) = throw CertificateException("Not a server")

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        private companion object {
            const val HTTPS_PORT = 443
        }
    }

private const val SAN_DNS = 2
private const val SAN_IP = 7

private fun String.isIpLiteral() = ':' in this || all { it.isDigit() || it == '.' }

/** RFC 6125 subset: SAN DNS names (single left-most `*` label) or SAN IP addresses. CN is ignored, like OkHttp. */
internal fun certificateMatchesHost(
    cert: X509Certificate,
    host: String,
): Boolean {
    val names = runCatching { cert.subjectAlternativeNames }.getOrNull().orEmpty()
    return if (host.isIpLiteral()) {
        val wanted = runCatching { InetAddress.getByName(host) }.getOrNull()
        names.any { it[0] == SAN_IP && runCatching { InetAddress.getByName(it[1] as String) }.getOrNull() == wanted }
    } else {
        names.filter { it[0] == SAN_DNS }.any { dnsMatches((it[1] as String).lowercase().trimEnd('.'), host) }
    }
}

private fun dnsMatches(
    pattern: String,
    host: String,
): Boolean =
    if (pattern.startsWith("*.")) {
        val suffix = pattern.substring(1) // ".example.com"
        host.endsWith(suffix) && host.length > suffix.length && '.' !in host.removeSuffix(suffix)
    } else {
        pattern == host
    }
