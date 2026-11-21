package com.nl.kaviyy.feature.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ReaderSettingsRepository
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.network.ReadingProfileDto
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ReaderPrefsTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private var load: AppResult<JsonObject> =
        AppResult.Success(
            Json.parseToJsonElement("""{"id":1,"kind":0}""").jsonObject,
        )
    private var offline = false
    private val saved = mutableListOf<ReadingProfileDto>()

    private val repo =
        object : ReaderSettingsRepository {
            override fun settings(
                server: Server,
                seriesId: Int,
            ) = emptyFlow<ReadingProfileDto>()

            override suspend fun refresh(
                server: Server,
                libraryId: Int,
                seriesId: Int,
            ) = AppResult.Success(Unit)

            override suspend fun update(
                server: Server,
                libraryId: Int,
                seriesId: Int,
                settings: ReadingProfileDto,
            ) = AppResult.Success(Unit)

            override suspend fun defaultProfile(server: Server) = load

            override suspend fun saveDefault(
                server: Server,
                raw: JsonObject,
                settings: ReadingProfileDto,
            ): AppResult<JsonObject> {
                if (offline) return AppResult.Failure(AppError.Network)
                saved += settings
                return AppResult.Success(settings.mergeInto(raw))
            }
        }

    private val session =
        object : AuthSession {
            override val servers = active.map { listOfNotNull(it) }
            override val activeServer = active

            override suspend fun switchTo(serverId: ServerId) = Unit

            override suspend fun remove(serverId: ServerId) = Unit

            override suspend fun deleteAllLocalData() = Unit

            override suspend fun knownServer(url: ServerUrl): Server? = null

            override suspend fun signIn(
                target: Server,
                method: LoginMethod,
                staySignedIn: Boolean,
            ): AppResult<Server> = error("unused")

            override suspend fun signOut(serverId: ServerId) = Unit

            override fun credentialSource(serverId: ServerId) = error("unused")
        }

    @Test
    fun changesSaveInOrderAndAFailedOneIsUndone() =
        runTest {
            val vm = ReaderPrefsViewModel(session, repo)
            runCurrent()
            vm.change { it.copy(readingDirection = 1) }
            vm.change { it.copy(readerMode = 2) }
            runCurrent()
            assertEquals(listOf(1 to 0, 1 to 2), saved.map { it.readingDirection to it.readerMode })
            offline = true
            vm.change { it.copy(emulateBook = true) }
            runCurrent()
            assertEquals(
                false,
                vm.state.value.settings
                    ?.emulateBook,
            )
            assertEquals(true, vm.state.value.saveFailed)
        }

    @Test
    fun offlineLoadSaysSo() =
        runTest {
            load = AppResult.Failure(AppError.Network)
            val vm = ReaderPrefsViewModel(session, repo)
            runCurrent()
            assertEquals(ReaderPrefsUiState(failed = true), vm.state.value)
        }

    @Test
    fun segmentsMapToKavitaValues() {
        var s = ReadingProfileDto()
        compose.setContent {
            KavitaTheme { ReaderPrefsScreen(ReaderPrefsUiState(ReadingProfileDto()), {}, { s = it(s) }) }
        }
        compose.onNodeWithText("Height").performClick()
        compose.onNodeWithText("Double (manga)").performClick()
        assertEquals(0 to 3, s.scalingOption to s.layoutMode)
    }

    @Test
    fun webtoonHidesWhatDoesNotApplyAndUpDownCountsAsPaged() {
        var s = ReadingProfileDto(readerMode = 2, bookReaderLayoutMode = 2)
        compose.setContent { KavitaTheme { ReaderPrefsScreen(ReaderPrefsUiState(s), {}, { s = it(s) }) } }
        compose.onNodeWithText("Reading direction").assertDoesNotExist()
        compose.onNodeWithText("Page layout").assertDoesNotExist()
        compose.onNodeWithText("Swipe to turn pages").assertDoesNotExist()
        compose.onNodeWithText("Close menu automatically").assertExists()
        compose.onAllNodesWithText("Paged")[0].performClick()
        assertEquals(0, s.readerMode)
        // Paged books keep the web's two columns.
        assertEquals(2, s.bookReaderLayoutMode)
    }

    @Test
    fun upDownFromTheWebShowsAsPagedWithItsOptions() {
        compose.setContent {
            KavitaTheme { ReaderPrefsScreen(ReaderPrefsUiState(ReadingProfileDto(readerMode = 1)), {}, {}) }
        }
        compose.onNodeWithText("Reading direction").assertExists()
        compose.onNodeWithText("Vertical").assertExists() // only as a PDF option in the web-only section
    }

    @Test
    @Config(qualifiers = "w412dp-h1900dp-xhdpi") // the whole page
    fun screenshotReaderPrefsLight() = shot("reader_prefs_light", Appearance.Light)

    @Test
    @Config(qualifiers = "w412dp-h1900dp-xhdpi") // the whole page
    fun screenshotReaderPrefsDark() = shot("reader_prefs_dark", Appearance.Dark)

    private fun shot(
        name: String,
        appearance: Appearance,
    ) {
        compose.setContent {
            KavitaTheme(appearance = appearance) {
                ReaderPrefsScreen(ReaderPrefsUiState(ReadingProfileDto(readingDirection = 1)), {}, {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
