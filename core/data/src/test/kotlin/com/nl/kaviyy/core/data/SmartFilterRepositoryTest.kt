package com.nl.kaviyy.core.data

import androidx.paging.PagingData
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.DashboardStreamDto
import com.nl.kaviyy.core.network.DecodeFilterDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.network.SideNavStreamDto
import com.nl.kaviyy.core.network.SmartFilterDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SmartFilterRepositoryTest {
    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val filters =
        mutableListOf(SmartFilterDto(1, "Old", "enc1"), SmartFilterDto(2, "People", "x", entityType = 1))
    private val home = mutableListOf<DashboardStreamDto>()
    private val nav = mutableListOf<SideNavStreamDto>()
    private val calls = mutableListOf<String>()
    private var decoded = JsonObject(emptyMap())
    private var failSave = false

    private val api =
        object : FakeKavitaApi() {
            override suspend fun smartFilters() = filters.toList()

            override suspend fun saveSmartFilter(filter: SeriesFilterV2Dto) {
                if (failSave) throw IOException("offline")
                calls += "save ${filter.id} ${filter.name}"
                if (filters.none { it.name == filter.name }) filters += SmartFilterDto(9, filter.name, "enc9")
            }

            override suspend fun deleteSmartFilter(filterId: Int) {
                calls += "delete $filterId"
            }

            override suspend fun dashboard(visibleOnly: Boolean) = home.toList()

            override suspend fun sideNav(visibleOnly: Boolean) = nav.toList()

            override suspend fun addDashboardStream(smartFilterId: Int): DashboardStreamDto {
                calls += "home+ $smartFilterId"
                return DashboardStreamDto(50, smartFilterId = smartFilterId).also { home += it }
            }

            override suspend fun removeDashboardStream(dashboardStreamId: Int) {
                calls += "home- $dashboardStreamId"
            }

            override suspend fun addSideNavStream(smartFilterId: Int): SideNavStreamDto {
                calls += "nav+ $smartFilterId"
                return SideNavStreamDto(60, smartFilterId = smartFilterId).also { nav += it }
            }

            override suspend fun removeSideNavStream(sideNavStreamId: Int) {
                calls += "nav- $sideNavStreamId"
            }

            override suspend fun decodeFilter(body: DecodeFilterDto) = decoded
        }

    private val catalog =
        object : CatalogRepository {
            override fun libraries(server: Server): Flow<List<LibraryEntity>> = error("unused")

            override suspend fun refreshLibraries(server: Server): AppResult<Unit> = error("unused")

            override fun librarySeries(
                server: Server,
                query: LibraryQuery,
            ): Flow<PagingData<SeriesEntity>> = error("unused")

            override suspend fun filterOptions(
                server: Server,
                source: OptionSource,
                libraryIds: List<Int>,
            ): AppResult<List<RuleValue>> = AppResult.Success(listOf(RuleValue("7", "Drama")))

            override fun seriesDetail(
                server: Server,
                seriesId: Int,
            ): Flow<SeriesDetail> = error("unused")

            override suspend fun refreshSeries(
                server: Server,
                seriesId: Int,
            ): AppResult<Unit> = error("unused")

            override suspend fun relatedSeries(
                server: Server,
                seriesId: Int,
            ): AppResult<List<RelatedSeries>> = error("unused")
        }

    private val repo = KavitaSmartFilterRepository({ api }, catalog)

    @Test
    fun decodedFiltersRoundTripTheStatementsWeSend() {
        val filter =
            SeriesFilter(
                matchAll = false,
                rules =
                    listOf(
                        FilterRule(FilterField.Genres, Comparison.Contains, listOf(RuleValue("7"), RuleValue("8"))),
                        FilterRule(FilterField.ReleaseYear, Comparison.GreaterThan, listOf(RuleValue("2015"))),
                    ),
                sort = SeriesSort.Title,
                libraryIds = listOf(3, 4),
            )
        val json = Json.encodeToJsonElement(SeriesFilterV2Dto.serializer(), filter.toDto(emptyList())).jsonObject
        assertEquals(filter, json.toSeriesFilter())
        // Unknown fields/comparisons from newer servers are dropped, not fatal.
        val odd =
            Json.parseToJsonElement(
                """{"statements":[{"field":99,"comparison":0,"value":"x"}],"combination":1}""",
            )
        assertEquals(SeriesFilter(), odd.jsonObject.toSeriesFilter())
    }

    @Test
    fun saveCreatesThenAddsHomeAndLibraryStreams() =
        runTest {
            val saved =
                repo.save(
                    server,
                    " Fantasy ",
                    SeriesFilter(),
                    listOf(3),
                    FilterPlacement(onHome = true, inLibrary = true),
                )
            assertEquals(AppResult.Success(SavedFilter(9, "Fantasy", "enc9")), saved)
            assertEquals(listOf("save 0 Fantasy", "home+ 9", "nav+ 9"), calls)
            assertEquals(AppResult.Success(FilterPlacement(onHome = true, inLibrary = true)), repo.placement(server, 9))
            assertEquals(AppResult.Success(listOf(SavedFilter(9, "Fantasy", "enc9"))), repo.pinned(server))
        }

    @Test
    fun savingUnderAnExistingNameUpdatesItAndRemovesUncheckedStreams() =
        runTest {
            home += DashboardStreamDto(40, smartFilterId = 1)
            nav += SideNavStreamDto(41, smartFilterId = 1)
            repo.save(server, "old", SeriesFilter(), emptyList(), FilterPlacement())
            assertEquals(listOf("save 1 old", "home- 40", "nav- 41"), calls)
        }

    @Test
    fun listSkipsNonSeriesFiltersAndLoadResolvesLabels() =
        runTest {
            assertEquals(AppResult.Success(listOf(SavedFilter(1, "Old", "enc1"))), repo.list(server))
            decoded =
                Json
                    .parseToJsonElement(
                        """{"statements":[{"field":18,"comparison":5,"value":"7,99"}],"combination":1}""",
                    ).jsonObject
            val loaded =
                assertIs<AppResult.Success<SeriesFilter>>(
                    repo.load(server, SavedFilter(1, "Old", "enc1")),
                ).value
            assertEquals(listOf(RuleValue("7", "Drama"), RuleValue("99")), loaded.rules.single().values)
            failSave = true
            assertEquals(
                AppResult.Failure(AppError.Network),
                repo.save(server, "x", SeriesFilter(), emptyList(), FilterPlacement()),
            )
            assertEquals(AppResult.Success(Unit), repo.delete(server, 1))
            assertEquals("delete 1", calls.last())
        }
}
