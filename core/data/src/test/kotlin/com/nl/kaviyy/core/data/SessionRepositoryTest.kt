package com.nl.kaviyy.core.data

import androidx.datastore.core.DataStore
import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.datastore.CredentialFile
import com.nl.kaviyy.core.datastore.CredentialStore
import com.nl.kaviyy.core.model.auth.Credentials
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.PinnedCertificate
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.network.LoginResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {
    private class MemoryDataStore : DataStore<CredentialFile> {
        override val data = MutableStateFlow(CredentialFile())

        override suspend fun updateData(transform: suspend (CredentialFile) -> CredentialFile): CredentialFile {
            data.update { transform(it) }
            return data.value
        }
    }

    private val db =
        Room
            .inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                KaviyyDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
    private val disk = MemoryDataStore()
    private val store = CredentialStore(disk)
    private val jwt = Credentials.Jwt("aarav", "t", "r", "k")
    private var login: AppResult<LoginResult> = AppResult.Success(LoginResult(jwt, setOf("Download")))
    private val session = SessionRepository(db.serverDao(), store) { _, _ -> login }
    private val url = (ServerUrl.parse("https://kavita.home.lan:5000") as ServerUrlResult.Valid).url

    @After
    fun tearDown() = db.close()

    private val target = Server(ServerId(0), "kavita.home.lan", url, "0.9.0.2")

    private suspend fun signIn(stay: Boolean = true) = session.signIn(target, LoginMethod.Password("aarav", "pw"), stay)

    @Test
    fun signInSavesServerAndCredentials() =
        runTest {
            assertNull(session.activeServer.first())
            val server = (signIn() as AppResult.Success).value
            assertEquals("kavita.home.lan", server.name)
            assertEquals("0.9.0.2", server.version)
            assertEquals(server, session.activeServer.first())
            assertEquals(jwt, store.credentials(server.id).first())
        }

    @Test
    fun signingInAgainReusesTheServerRow() =
        runTest {
            val first = (signIn() as AppResult.Success).value
            val second = (signIn() as AppResult.Success).value
            assertEquals(first.id, second.id)
        }

    @Test
    fun notStayingSignedInKeepsCredentialsInMemoryOnly() =
        runTest {
            val server = (signIn(stay = false) as AppResult.Success).value
            assertNull(store.credentials(server.id).first())
            assertEquals(server, session.activeServer.first())
            val source = session.credentialSource(server.id)
            assertEquals(jwt, withContext(Dispatchers.IO) { source.current() })
        }

    @Test
    fun failedLoginSavesNothing() =
        runTest {
            login = AppResult.Failure(AppError.Unauthorized)
            assertEquals(AppResult.Failure(AppError.Unauthorized), signIn())
            assertNull(session.activeServer.first())
            assertEquals(emptyList(), db.serverDao().observeAll().first())
        }

    @Test
    fun signOutClearsCredentialsButKeepsTheServer() =
        runTest {
            val server = (signIn() as AppResult.Success).value
            session.signOut(server.id)
            assertNull(session.activeServer.first())
            assertNull(store.credentials(server.id).first())
            assertEquals(
                1,
                db
                    .serverDao()
                    .observeAll()
                    .first()
                    .size,
            )
        }

    @Test
    fun trustDecisionsArePersistedAndKnown() =
        runTest {
            val pin = PinnedCertificate.of(byteArrayOf(9, 9))
            session.signIn(target.copy(pinnedCertificate = pin), LoginMethod.Password("a", "b"), true)
            assertEquals(pin, session.knownServer(url)?.pinnedCertificate)
            assertNull(session.knownServer((ServerUrl.parse("https://other.lan") as ServerUrlResult.Valid).url))
        }

    @Test
    fun authKeySignInStoresTheKey() =
        runTest {
            login = AppResult.Success(LoginResult(Credentials.AuthKey("pasted"), emptySet()))
            val server = (session.signIn(target, LoginMethod.AuthKey("pasted"), true) as AppResult.Success).value
            assertEquals(Credentials.AuthKey("pasted"), store.credentials(server.id).first())
        }

    @Test
    fun refreshedTokensFollowTheStorageMode() =
        runTest {
            val server = (signIn() as AppResult.Success).value
            val refreshed = jwt.copy(token = "t2")
            withContext(Dispatchers.IO) { session.credentialSource(server.id).update(refreshed) }
            assertIs<Credentials.Jwt>(store.credentials(server.id).first()).also { assertEquals("t2", it.token) }
        }
}
