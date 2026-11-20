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
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import javax.inject.Inject

/** `GET api/Server/server-info-slim` (ServerInfoSlimDto, required: installId, kavitaVersion). */
@Serializable
internal data class ServerInfoSlimDto(
    val installId: String? = null,
    val kavitaVersion: String? = null,
)

/**
 * @property version null when unknown: unparseable, or the server requires sign-in for this endpoint. The Connect
 * screen shows "version unknown" (and re-checks after sign-in) rather than blocking.
 */
data class ServerInfo(
    val version: KavitaVersion?,
) {
    val isSupported: Boolean get() = version?.isSupported == true
}

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
                val request = Request.Builder().url(server.url.resolve("api/Server/server-info-slim")).build()
                // ponytail: blocking execute on IO; cancellation doesn't abort the call (bounded by timeouts).
                val response = appResult { clients.create(server).newCall(request).execute() }
                when (response) {
                    is AppResult.Failure -> {
                        response
                    }

                    is AppResult.Success -> {
                        response.value.use { r ->
                            if (r.code == HTTP_UNAUTHORIZED || r.code == HTTP_FORBIDDEN) {
                                // Reached a server that wants auth for this call: reachable, version unknown.
                                AppResult.Success(ServerInfo(version = null))
                            } else if (!r.isSuccessful) {
                                AppResult.Failure(AppError.fromHttpCode(r.code))
                            } else {
                                appResult {
                                    val dto = json.decodeFromString<ServerInfoSlimDto>(r.body.string())
                                    // A non-Kavita server answering 200 with other JSON must not pass as Kavita.
                                    require(
                                        dto.installId != null || dto.kavitaVersion != null,
                                    ) { "Not a Kavita server" }
                                    ServerInfo(KavitaVersion.parse(dto.kavitaVersion))
                                }
                            }
                        }
                    }
                }
            }

        private companion object {
            val json = Json { ignoreUnknownKeys = true }
        }
    }
