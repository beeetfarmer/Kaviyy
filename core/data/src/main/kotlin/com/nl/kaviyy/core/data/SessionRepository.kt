package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.ServerDao
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.database.toModel
import com.nl.kaviyy.core.datastore.CredentialStore
import com.nl.kaviyy.core.model.auth.Credentials
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.network.AccountApi
import com.nl.kaviyy.core.network.CredentialSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** Sign-in state for the app; ViewModels depend on this, [SessionRepository] implements it. */
interface AuthSession {
    /** The most recently used server that has credentials, or null (show Connect). */
    val activeServer: Flow<Server?>

    /**
     * Signs in and remembers the server. With [staySignedIn] false the credentials are kept in memory only,
     * so the user is signed out when the process dies.
     */
    suspend fun signIn(
        url: ServerUrl,
        version: String?,
        username: String,
        password: String,
        staySignedIn: Boolean,
    ): AppResult<Server>

    suspend fun signOut(serverId: ServerId)

    /** Credentials for the HTTP layer (blocking; OkHttp threads only). */
    fun credentialSource(serverId: ServerId): CredentialSource
}

@Singleton
class SessionRepository
    @Inject
    constructor(
        private val servers: ServerDao,
        private val store: CredentialStore,
        private val accounts: AccountApi,
    ) : AuthSession {
        private val memoryOnly = MutableStateFlow<Map<ServerId, Credentials>>(emptyMap())

        override val activeServer: Flow<Server?> =
            combine(servers.observeAll(), store.serverIds, memoryOnly) { rows, stored, memory ->
                rows.firstOrNull { ServerId(it.id) in stored || ServerId(it.id) in memory }?.toModel()
            }.distinctUntilChanged()

        override suspend fun signIn(
            url: ServerUrl,
            version: String?,
            username: String,
            password: String,
            staySignedIn: Boolean,
        ): AppResult<Server> {
            val existing = servers.byUrl(url.value)
            // Keep an existing pin / HTTP opt-in for this address; a new server starts strict.
            val probe = existing?.toModel() ?: Server(ServerId(0), url.host, url, version)
            return when (val result = accounts.login(probe, username, password)) {
                is AppResult.Failure -> {
                    result
                }

                is AppResult.Success -> {
                    val row = (existing ?: ServerEntity(name = url.host, url = url.value))
                    val id =
                        ServerId(
                            servers.upsert(
                                row.copy(version = version ?: row.version, lastUsedAt = System.currentTimeMillis()),
                            ),
                        )
                    val saved = existing?.let { ServerId(it.id) } ?: id
                    save(saved, result.value.credentials, staySignedIn)
                    AppResult.Success(checkNotNull(servers.byId(saved.value)?.toModel()))
                }
            }
        }

        private suspend fun save(
            id: ServerId,
            credentials: Credentials,
            persist: Boolean,
        ) {
            if (persist) {
                memoryOnly.update { it - id }
                store.save(id, credentials)
            } else {
                store.clear(id)
                memoryOnly.update { it + (id to credentials) }
            }
        }

        override suspend fun signOut(serverId: ServerId) {
            memoryOnly.update { it - serverId }
            store.clear(serverId)
        }

        override fun credentialSource(serverId: ServerId): CredentialSource =
            object : CredentialSource {
                override fun current(): Credentials? =
                    memoryOnly.value[serverId] ?: runBlocking { store.credentials(serverId).first() }

                override fun update(credentials: Credentials.Jwt) =
                    runBlocking { save(serverId, credentials, persist = serverId !in memoryOnly.value) }

                override fun signOut() = runBlocking { this@SessionRepository.signOut(serverId) }
            }
    }
