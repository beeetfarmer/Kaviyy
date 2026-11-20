package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.KavitaVersion
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerProbeTest {
    private val mock = MockWebServer().apply { start() }
    private val server =
        Server(
            ServerId(1),
            "S",
            (ServerUrl.parse(mock.url("/").toString()) as ServerUrlResult.Valid).url,
            allowInsecure = true,
        )

    @After
    fun tearDown() = mock.close()

    private fun probe(
        body: String? = null,
        code: Int = 200,
    ): AppResult<ServerInfo> =
        kotlinx.coroutines.runBlocking {
            if (body != null || code != 200) mock.enqueue(MockResponse(code = code, body = body.orEmpty()))
            ServerProbe(ServerHttpClients(baseHttpClient()), kotlinx.coroutines.Dispatchers.IO).probe(server)
        }

    @Test
    fun supportedServer() {
        val result = probe("""{"installId":"abc","kavitaVersion":"0.9.0.2","isDocker":true}""")
        val info = (result as AppResult.Success).value
        assertEquals(KavitaVersion.parse("0.9.0.2"), info.version)
        assertTrue(info.isSupported)
        assertEquals("/api/Server/server-info-slim", mock.takeRequest().url.encodedPath)
    }

    @Test
    fun ssoConfigIsReadWhenEnabled() {
        mock.enqueue(MockResponse(body = """{"installId":"a","kavitaVersion":"0.9.0.2"}"""))
        mock.enqueue(
            MockResponse(body = """{"enabled":true,"providerName":"Authelia","disablePasswordAuthentication":true}"""),
        )
        val info = (probe() as AppResult.Success).value
        assertEquals(SsoInfo("Authelia", passwordLoginDisabled = true), info.sso)
        mock.takeRequest()
        assertEquals("/api/Settings/oidc", mock.takeRequest().url.encodedPath)
    }

    @Test
    fun ssoDisabledOrMissingEndpointMeansNoSso() {
        mock.enqueue(MockResponse(body = """{"installId":"a","kavitaVersion":"0.9.0.2"}"""))
        mock.enqueue(MockResponse(body = """{"enabled":false,"providerName":"X"}"""))
        assertEquals(null, (probe() as AppResult.Success).value.sso)
        mock.enqueue(MockResponse(body = """{"installId":"a","kavitaVersion":"0.9.0.2"}"""))
        mock.enqueue(MockResponse(code = 404))
        assertEquals(null, (probe() as AppResult.Success).value.sso)
    }

    @Test
    fun oldServerIsReachableButUnsupported() {
        val info = (probe("""{"installId":"abc","kavitaVersion":"0.8.6.0"}""") as AppResult.Success).value
        assertFalse(info.isSupported)
    }

    @Test
    fun nonKavitaResponsesAreInvalid() {
        assertEquals(AppResult.Failure(AppError.InvalidData), probe("<html>router login</html>"))
        assertEquals(AppResult.Failure(AppError.InvalidData), probe("""{"hello":"world"}"""))
    }

    @Test
    fun httpErrorsMapToAppErrors() {
        assertEquals(AppResult.Failure(AppError.NotFound), probe(code = 404))
        assertEquals(AppResult.Failure(AppError.Server(502)), probe(code = 502))
    }

    @Test
    fun authRequiredMeansReachableWithUnknownVersion() {
        val info = (probe(code = 401) as AppResult.Success).value
        assertEquals(null, info.version)
        assertFalse(info.isSupported)
    }

    @Test
    fun unreachableServerIsANetworkError() {
        mock.close()
        assertEquals(AppResult.Failure(AppError.Network), probe())
    }

    @Test
    fun cleartextWithoutOptInIsRefused() =
        runTest {
            val strict = server.copy(allowInsecure = false)
            val result =
                ServerProbe(
                    ServerHttpClients(baseHttpClient()),
                    StandardTestDispatcher(testScheduler),
                ).probe(strict)
            assertEquals(AppResult.Failure(AppError.Network), result)
            assertEquals(0, mock.requestCount)
        }
}
