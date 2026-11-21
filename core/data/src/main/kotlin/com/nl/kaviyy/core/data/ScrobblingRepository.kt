package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ScrobbleHoldDto
import com.nl.kaviyy.core.network.apiCall
import javax.inject.Inject

private const val PROVIDER_ANILIST = 1
private const val PROVIDER_MAL = 2

enum class ScrobbleProvider { AniList, Mal }

enum class LinkState { Connected, Expired, NotConnected }

data class ScrobbleStatus(
    /** Kavita+ active; without it nothing else is fetched. */
    val licensed: Boolean,
    val links: Map<ScrobbleProvider, LinkState> = emptyMap(),
    val holds: List<ScrobbleHoldDto> = emptyList(),
)

/** Kavita+ scrobbling, read-only (connecting accounts stays in the web UI). Online only. */
interface ScrobblingRepository {
    suspend fun status(server: Server): AppResult<ScrobbleStatus>
}

class KavitaScrobblingRepository
    @Inject
    constructor(
        private val apis: ServerApis,
    ) : ScrobblingRepository {
        override suspend fun status(server: Server) =
            apiCall {
                val api = apis.api(server)
                if (!api.validLicense()) return@apiCall ScrobbleStatus(false)
                // The token is read into memory only to see whether it is set; it is never stored or logged.
                val aniList = api.aniListToken().use { it.string().trim('"', ' ', '\n') }.isNotEmpty()
                val mal = !api.malAccount().username.isNullOrBlank()
                ScrobbleStatus(
                    true,
                    mapOf(
                        ScrobbleProvider.AniList to link(aniList) { api.scrobbleTokenExpired(PROVIDER_ANILIST) },
                        ScrobbleProvider.Mal to link(mal) { api.scrobbleTokenExpired(PROVIDER_MAL) },
                    ),
                    api.scrobbleHolds(),
                )
            }

        private suspend fun link(
            set: Boolean,
            expired: suspend () -> Boolean,
        ) = when {
            !set -> LinkState.NotConnected
            expired() -> LinkState.Expired
            else -> LinkState.Connected
        }
    }
