package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.server.PinnedCertificate
import com.nl.kaviyy.core.model.server.Server
import okhttp3.ConnectionSpec
import okhttp3.Handshake
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.tls.HandshakeCertificates
import java.io.IOException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLPeerUnverifiedException

/** N2: an http:// request the server's policy does not allow (no opt-in, public host, or another host). */
class CleartextNotPermittedException(
    host: String,
) : IOException("Cleartext HTTP not permitted for $host")

/** N3: the server presented a certificate chain that does not contain the pinned certificate. Hard fail. */
class PinMismatchException(
    host: String,
) : SSLPeerUnverifiedException("Certificate for $host does not match the pinned certificate")

private const val CONNECT_TIMEOUT_S = 15L
private const val IO_TIMEOUT_S = 30L

/** Shared base (one connection pool + dispatcher). Never used directly for server calls: see [ServerHttpClients]. */
fun baseHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(IO_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(IO_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

/**
 * The only way to get an HTTP client for a server (SECURITY_CHECKLIST N1–N5): TLS 1.2+ only, cleartext only when
 * [Server.cleartextPermitted], TOFU pin trusted and enforced, no https→http redirects, JSON size limit.
 * Hostname verification is OkHttp's default and is never replaced (N4).
 */
@Singleton
class ServerHttpClients
    @Inject
    constructor(
        private val base: OkHttpClient,
    ) {
        /** Unauthenticated client (server probe, login, token refresh). */
        fun create(server: Server): OkHttpClient =
            base
                .newBuilder()
                .connectionSpecs(
                    if (server.cleartextPermitted) {
                        listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)
                    } else {
                        listOf(ConnectionSpec.MODERN_TLS)
                    },
                ).followSslRedirects(false)
                .addInterceptor(JsonSizeLimitInterceptor())
                .addNetworkInterceptor(TransportPolicyInterceptor(server))
                .apply {
                    server.pinnedCertificate?.let { pin ->
                        val trust =
                            HandshakeCertificates
                                .Builder()
                                .addTrustedCertificate(pin.x509())
                                .addPlatformTrustedCertificates()
                                .build()
                        sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
                    }
                }.build()

        /** Authenticated client: auth headers for the server's own host only; stripped on any other hop. */
        fun create(
            server: Server,
            credentials: CredentialSource,
        ): OkHttpClient {
            val plain = create(server)
            return plain
                .newBuilder()
                .addInterceptor(AuthInterceptor(server, credentials))
                .authenticator(TokenAuthenticator(server, credentials, plain))
                .build()
        }
    }

/** Runs on every network hop, redirects included, before the request is written. */
internal class TransportPolicyInterceptor(
    private val server: Server,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val sameHost = url.isServer(server)
        if (!url.isHttps && !(sameHost && server.cleartextPermitted)) throw CleartextNotPermittedException(url.host)
        val pin = server.pinnedCertificate?.takeIf { url.isHttps && sameHost }
        if (pin != null && !chain.connection()?.handshake().containsPin(pin)) throw PinMismatchException(url.host)
        // N5/N6: credentials never follow a redirect to another host (OkHttp only strips Authorization itself).
        val outgoing =
            if (sameHost) {
                request
            } else {
                request
                    .newBuilder()
                    .url(url.newBuilder().removeAllQueryParameters(API_KEY_PARAM).build())
                    .removeHeader(AUTHORIZATION)
                    .removeHeader(API_KEY)
                    .build()
            }
        return chain.proceed(outgoing)
    }
}

/** True if any certificate the peer presented is the pinned one (the chain itself was already validated). */
internal fun Handshake?.containsPin(pin: PinnedCertificate): Boolean =
    this?.peerCertificates.orEmpty().any { PinnedCertificate.sha256Hex(it.encoded) == pin.sha256 }

internal fun PinnedCertificate.x509(): X509Certificate =
    CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
