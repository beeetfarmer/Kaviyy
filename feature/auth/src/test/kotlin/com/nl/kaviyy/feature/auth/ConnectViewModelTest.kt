package com.nl.kaviyy.feature.auth

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.KavitaVersion
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerUrlError
import com.nl.kaviyy.core.network.ServerInfo
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val probed = mutableListOf<Server>()
    private var answer: AppResult<ServerInfo> = AppResult.Success(ServerInfo(KavitaVersion.parse("0.9.0.2")))

    private fun viewModel() =
        ConnectViewModel { server ->
            probed += server
            answer
        }

    private fun ConnectViewModel.type(text: String) {
        address.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun probesOnlyAfterTypingPauses() =
        runTest {
            val vm = viewModel()
            runCurrent()
            vm.type("kavita.home.la")
            advanceTimeBy(300)
            vm.type("kavita.home.lan")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(1, probed.size)
            assertEquals("https://kavita.home.lan", probed.single().url.value)
            assertFalse(probed.single().allowInsecure)
            val found = assertIs<Reachability.Found>(vm.state.value.reachability)
            assertEquals("0.9.0.2", found.version)
            assertTrue(found.supported)
        }

    @Test
    fun invalidAndHttpAddressesAreNotProbed() =
        runTest {
            val vm = viewModel()
            vm.type("ftp://x")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(Reachability.Invalid(ServerUrlError.UnsupportedScheme), vm.state.value.reachability)
            vm.type("http://192.168.1.2:5000")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(Reachability.InsecureHttp, vm.state.value.reachability)
            vm.type("")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(Reachability.Idle, vm.state.value.reachability)
            assertTrue(probed.isEmpty())
        }

    @Test
    fun failuresAndOutdatedServersAreReported() =
        runTest {
            answer = AppResult.Failure(AppError.Tls)
            val vm = viewModel()
            vm.type("k.example")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(Reachability.Failed(AppError.Tls), vm.state.value.reachability)
            answer = AppResult.Success(ServerInfo(KavitaVersion.parse("0.8.6")))
            vm.type("k2.example")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertFalse(assertIs<Reachability.Found>(vm.state.value.reachability).supported)
        }

    @Test
    fun signInNeedsAReachableServerAndCredentials() =
        runTest {
            val vm = viewModel()
            vm.type("k.example")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertFalse(vm.state.value.canSignIn)
            vm.username.setTextAndPlaceCursorAtEnd("aarav")
            vm.password.setTextAndPlaceCursorAtEnd("hunter2")
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertTrue(vm.state.value.canSignIn)
            vm.setStaySignedIn(false)
            assertFalse(vm.state.value.staySignedIn)
        }
}
