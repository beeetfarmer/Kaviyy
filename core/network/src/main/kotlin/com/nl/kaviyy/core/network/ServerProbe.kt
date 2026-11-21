package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.appResult
import com.nl.kaviyy.core.model.server.KavitaVersion
import com.nl.kaviyy.core.model.server.Server
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import javax.inject.Inject

/** `GET api/Server/server-info-slim` (ServerInfoSlimDto, required: installId, kavitaVersion). */
@Serializable
data class ServerInfoSlimDto(
    val installId: String? = null,
    val kavitaVersion: String? = null,
    val isDocker: Boolean = false,
    val firstInstallDate: String? = null,
)

/**
 * @property version null when unknown: unparseable, or the server requires sign-in for this endpoint. The Connect
 * screen shows "version unknown" (and re-checks after sign-in) rather than blocking.
 */
data class ServerInfo(
    val version: KavitaVersion?,
    /** Set when the server has OIDC single sign-on enabled (`api/Settings/oidc`). */
    val sso: SsoInfo? = null,
) {
    val isSupported: Boolean get() = version?.isSupported == true
}

/** `GET api/Settings/oidc` (OidcPublicConfigDto, public). */
@Serializable
internal data class OidcPublicConfigDto(
    val enabled: Boolean = false,
    val providerName: String? = null,
    val disablePasswordAuthentication: Boolean = false,
)

data class SsoInfo(
    val providerName: String?,
    val passwordLoginDisabled: Boolean,
)

/** Reachability + version check (the seam ViewModels depend on; [ServerProbe] is the implementation). */
fun interface ServerProber {
    suspend fun probe(server: Server): AppResult<ServerInfo>
}

/** Reachability + version check used by the Connect screen. Unauthenticated; goes through the server's policy. */
class ServerProbe
    @Inject
    constructor(
        private val clients: ServerHttpClients,
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
    ) : ServerProber {
        override suspend fun probe(server: Server): AppResult<ServerInfo> =
            withContext(io) {
                when (val info = version(server)) {
                    is AppResult.Success -> AppResult.Success(info.value.copy(sso = sso(server)))
                    is AppResult.Failure -> info
                }
            }

        /** Best effort: any failure means "no SSO info" (older servers lack the endpoint). */
        private fun sso(server: Server): SsoInfo? {
            val request = Request.Builder().url(server.url.resolve("api/Settings/oidc")).build()
            val dto =
                runCatching {
                    clients.create(server).newCall(request).execute().use { r ->
                        if (r.isSuccessful) json.decodeFromString<OidcPublicConfigDto>(r.body.string()) else null
                    }
                }.getOrNull()
            return dto
                ?.takeIf {
                    it.enabled
                }?.let { SsoInfo(it.providerName?.take(MAX_PROVIDER_NAME), it.disablePasswordAuthentication) }
        }

        private fun version(server: Server): AppResult<ServerInfo> {
            val request = Request.Builder().url(server.url.resolve("api/Server/server-info-slim")).build()
            // ponytail: blocking execute on IO; cancellation doesn't abort the call (bounded by timeouts).
            val response = appResult { clients.create(server).newCall(request).execute() }
            return if (response is AppResult.Success) {
                response.value.use(
                    ::parseVersion,
                )
            } else {
                response as AppResult.Failure
            }
        }

        private fun parseVersion(r: Response): AppResult<ServerInfo> =
            when {
                // Reached a server that wants auth for this call: reachable, version unknown.
                r.code == HTTP_UNAUTHORIZED || r.code == HTTP_FORBIDDEN -> {
                    AppResult.Success(ServerInfo(version = null))
                }

                !r.isSuccessful -> {
                    AppResult.Failure(AppError.fromHttpCode(r.code))
                }

                else -> {
                    appResult {
                        val dto = json.decodeFromString<ServerInfoSlimDto>(r.body.string())
                        // A non-Kavita server answering 200 with other JSON must not pass as Kavita.
                        require(dto.installId != null || dto.kavitaVersion != null) { "Not a Kavita server" }
                        ServerInfo(KavitaVersion.parse(dto.kavitaVersion))
                    }
                }
            }

        private companion object {
            const val MAX_PROVIDER_NAME = 40
            val json = Json { ignoreUnknownKeys = true }
        }
    }
