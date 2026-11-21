package com.nl.kaviyy.core.data

import android.content.Context
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ServerDao
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.database.toModel
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.datastore.CredentialStore
import com.nl.kaviyy.core.datastore.TrackerStore
import com.nl.kaviyy.core.model.auth.Credentials
import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.network.AccountApi
import com.nl.kaviyy.core.network.CredentialSource
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.network.ServerImageLoaders
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Sign-in state for the app; ViewModels depend on this, [SessionRepository] implements it. */
interface AuthSession {
    /** Servers the user is signed in to, most recently used first. */
    val servers: Flow<List<Server>>

    /** The most recently used server that has credentials, or null (show Connect). */
    val activeServer: Flow<Server?>

    /** Makes [serverId] the active server. */
    suspend fun switchTo(serverId: ServerId)

    /** Signs out and forgets the server: its row, trust decisions and cached images. */
    suspend fun remove(serverId: ServerId)

    /** Signs out of every server and deletes everything stored locally (database, credentials, caches). */
    suspend fun deleteAllLocalData()

    /** A saved server for this address (its pin and HTTP opt-in), if any. */
    suspend fun knownServer(url: ServerUrl): Server?

    /**
     * Signs in (password or Auth Key) to [target] and remembers it with the trust decisions it carries (pin, HTTP
     * opt-in; its id is ignored). With [staySignedIn] false the credentials are kept in memory only, so the user is
     * signed out when the process dies.
     */
    suspend fun signIn(
        target: Server,
        method: LoginMethod,
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
        private val db: KaviyyDatabase,
        private val store: CredentialStore,
        private val accounts: AccountApi,
        private val settings: AppSettingsStore,
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
        private val trackers: TrackerStore,
    ) : AuthSession {
        private val dao: ServerDao = db.serverDao()
        private val memoryOnly = MutableStateFlow<Map<ServerId, Credentials>>(emptyMap())

        override val servers: Flow<List<Server>> =
            combine(dao.observeAll(), store.serverIds, memoryOnly) { rows, stored, memory ->
                rows.filter { ServerId(it.id) in stored || ServerId(it.id) in memory }.mapNotNull { it.toModel() }
            }.distinctUntilChanged()

        override val activeServer: Flow<Server?> = servers.map { it.firstOrNull() }.distinctUntilChanged()

        override suspend fun switchTo(serverId: ServerId) = dao.touch(serverId.value, System.currentTimeMillis())

        override suspend fun remove(serverId: ServerId) {
            val server = dao.byId(serverId.value)?.toModel()
            signOut(serverId)
            dao.delete(serverId.value)
            trackers.forgetServer(serverId.value)
            // Per-server data isolation: everything cached for a server lives under its id (paths from ids only).
            withContext(io) {
                server?.let { ServerImageLoaders.cacheDir(context, it).deleteRecursively() }
                ServerImageLoaders.clearMemory() // shared by all servers: drop it whole rather than leave this one's
                File(context.cacheDir, "books/${serverId.value}").deleteRecursively()
                File(context.filesDir, "downloads/${serverId.value}").deleteRecursively()
            }
        }

        override suspend fun deleteAllLocalData() {
            memoryOnly.value = emptyMap()
            store.clearAll()
            trackers.clearAll()
            settings.update { AppSettings() }
            withContext(io) {
                db.clearAllTables()
                // Everything this app writes outside Room and DataStore: covers, cached books, downloads, reader prefs.
                File(context.cacheDir, "images").deleteRecursively()
                ServerImageLoaders.clearMemory()
                File(context.cacheDir, "books").deleteRecursively()
                File(context.filesDir, "downloads").deleteRecursively()
                context.deleteSharedPreferences(SharedPrefsReaderDisplayRepository.FILE)
            }
        }

        override suspend fun knownServer(url: ServerUrl): Server? = dao.byUrl(url.value)?.toModel()

        override suspend fun signIn(
            target: Server,
            method: LoginMethod,
            staySignedIn: Boolean,
        ): AppResult<Server> {
            val existing = dao.byUrl(target.url.value)
            return when (val result = accounts.login(target, method)) {
                is AppResult.Failure -> {
                    result
                }

                is AppResult.Success -> {
                    val row =
                        (existing ?: ServerEntity(name = target.name, url = target.url.value)).copy(
                            version = target.version ?: existing?.version,
                            pinnedCertDerBase64 = target.pinnedCertificate?.derBase64,
                            allowInsecure = target.allowInsecure,
                            lastUsedAt = System.currentTimeMillis(),
                            username = result.value.username,
                            roles = result.value.roles.joinToString(","),
                        )
                    val inserted = dao.upsert(row)
                    val id = ServerId(existing?.id ?: inserted)
                    save(id, result.value.credentials, staySignedIn)
                    AppResult.Success(checkNotNull(dao.byId(id.value)?.toModel()))
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
