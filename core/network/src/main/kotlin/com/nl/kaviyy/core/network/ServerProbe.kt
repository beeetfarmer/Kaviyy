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
import javax.inject.Inject

/** `GET api/Server/server-info-slim` (ServerInfoSlimDto, required: installId, kavitaVersion). */
@Serializable
internal data class ServerInfoSlimDto(
    val installId: String? = null,
    val kavitaVersion: String? = null,
)

/** @property version null when the server reports something unparseable (treated as unsupported → warn). */
data class ServerInfo(
    val version: KavitaVersion?,
) {
    val isSupported: Boolean get() = version?.isSupported == true
}

/** Reachability + version check used by the Connect screen. Unauthenticated; goes through the server's policy. */
class ServerProbe
    @Inject
    constructor(
        private val clients: ServerHttpClients,
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
    ) {
        suspend fun probe(server: Server): AppResult<ServerInfo> =
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
                            if (!r.isSuccessful) {
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
