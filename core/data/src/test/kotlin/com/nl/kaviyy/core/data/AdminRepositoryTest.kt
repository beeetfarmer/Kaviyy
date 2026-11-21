package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.RefreshSeriesDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminRepositoryTest {
    private val url = (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url
    private val calls = mutableListOf<String>()
    private var posted: JsonObject? = null
    private var forbidden = false

    private val api =
        object : FakeKavitaApi() {
            override suspend fun refreshSeriesMetadata(body: RefreshSeriesDto) {
                if (forbidden) throw HttpException(Response.error<Any>(403, "".toResponseBody()))
                calls += "refresh $body"
            }

            override suspend fun scanSeries(body: RefreshSeriesDto) {
                calls += "scan $body"
            }

            override suspend fun rawSeriesMetadata(seriesId: Int) =
                Json
                    .parseToJsonElement(
                        """{"id":3,"seriesId":7,"summary":"old",""" +
                            """"genres":[{"id":1,"title":"F"}],"summaryLocked":false}""",
                    ).jsonObject

            override suspend fun updateSeriesMetadata(body: JsonObject) {
                posted = body
            }
        }

    private val repo = KavitaAdminRepository { api }
    private val server = Server(ServerId(1), "s", url, roles = setOf("Admin", "Download"))

    @Test
    fun adminRoleGatesTheUi() {
        assertTrue(server.isAdmin)
        assertFalse(server.copy(roles = setOf("Download")).isAdmin)
    }

    @Test
    fun jobsSendTheSeriesAndReportServerRefusal() =
        runTest {
            repo.refreshMetadata(server, 2, 7)
            repo.scan(server, 2, 7)
            val dto = RefreshSeriesDto(2, 7, forceUpdate = true)
            assertEquals(listOf("refresh $dto", "scan $dto"), calls)
            forbidden = true
            assertEquals(AppResult.Failure(AppError.Forbidden), repo.refreshMetadata(server, 2, 7))
        }

    @Test
    fun summaryEditKeepsEveryOtherFieldAndLocksTheSummary() =
        runTest {
            assertEquals(AppResult.Success(Unit), repo.updateSummary(server, 7, "  New summary  "))
            val meta = posted?.get("seriesMetadata")?.jsonObject
            assertEquals("New summary", meta?.get("summary")?.jsonPrimitive?.content)
            assertEquals("true", meta?.get("summaryLocked")?.jsonPrimitive?.content)
            assertEquals(setOf("id", "seriesId", "summary", "genres", "summaryLocked"), meta?.keys)
            assertEquals("""[{"id":1,"title":"F"}]""", meta?.get("genres").toString())
        }
}
