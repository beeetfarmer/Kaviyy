package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.CollectionAddDto
import com.nl.kaviyy.core.network.CollectionDto
import com.nl.kaviyy.core.network.CollectionRemoveDto
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmartCollectionsTest {
    private val rule =
        SmartRule(
            matchAll = false,
            tags = listOf(RuleValue("12", "Isekai"), RuleValue("40", "Time Travel")),
            genres = listOf(RuleValue("3", "Fantasy")),
        )

    @Test
    fun ruleRoundTripsThroughTheSummaryAndKeepsTheUsersText() {
        val summary = SmartRule.summaryWith("Comfy reads", rule)
        assertEquals("Comfy reads\n\n[smart-list match=any tags=12:Isekai,40:Time Travel genres=3:Fantasy]", summary)
        assertEquals(rule, SmartRule.from(summary))
        assertEquals("Comfy reads", SmartRule.userSummary(summary))
        assertEquals(
            rule.copy(matchAll = true, genres = emptyList()),
            SmartRule.from("[smart-list match=all tags=12:Isekai,40:Time Travel]"),
        )
        assertNull(SmartRule.from("Just a normal collection"))
        assertNull(SmartRule.from(null))
    }

    @Test
    fun anyUsesContainsWithOrAndAllUsesMustContainsWithAnd() {
        val any = rule.filter().toDto(emptyList())
        assertEquals(listOf(5 to 6, 5 to 18), any.statements.map { it.comparison to it.field })
        assertEquals("12,40", any.statements.first().value)
        val all = rule.copy(matchAll = true).filter().toDto(emptyList())
        assertEquals(listOf(6, 6), all.statements.map { it.comparison })
        assertEquals(true, all.combination != any.combination)
    }

    private val server =
        Server(ServerId(1), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url, username = "neo")
    private val added = mutableListOf<CollectionAddDto>()
    private val removed = mutableListOf<CollectionRemoveDto>()
    private val updated = mutableListOf<CollectionDto>()
    private var matches = listOf(1, 2, 3)
    private var members = listOf(2, 3, 4)

    private val api =
        object : FakeKavitaApi() {
            override suspend fun series(
                filter: SeriesFilterV2Dto,
                pageNumber: Int,
                pageSize: Int,
            ): List<SeriesDto> {
                // Collection membership queries filter on CollectionTags (field 7).
                val ids = if (filter.statements.any { it.field == 7 }) members else matches
                return if (pageNumber == 1) ids.map { SeriesDto(id = it, name = "S$it") } else emptyList()
            }

            override suspend fun addToCollection(body: CollectionAddDto) {
                added += body
            }

            override suspend fun removeFromCollection(body: CollectionRemoveDto) {
                removed += body
            }

            override suspend fun collections(sortByLastModified: Boolean) =
                listOf(
                    CollectionDto(5, "Isekai picks", owner = "other"),
                    CollectionDto(9, "Isekai picks", owner = "Neo"),
                )

            override suspend fun updateCollection(body: CollectionDto): CollectionDto = body.also { updated += it }
        }

    private val lists =
        object : ListsRepository by unusedLists() {
            override suspend fun refreshCollections(server: Server) = AppResult.Success(Unit)
        }
    private val smart = SmartCollections({ api }, lists)

    @Test
    fun createAddsTheMatchesThenStoresTheRuleOnTheNewCollection() =
        runTest {
            assertEquals(AppResult.Success(9), smart.create(server, " Isekai picks ", rule))
            assertEquals(CollectionAddDto(0, "Isekai picks", listOf(1, 2, 3)), added.single())
            assertEquals(9, updated.single().id) // ours, not the other user's same-named one
            assertEquals(rule, SmartRule.from(updated.single().summary))
        }

    @Test
    fun syncAddsNewMatchesAndRemovesOnesThatNoLongerMatch() =
        runTest {
            val c = CollectionDto(9, "Isekai picks", summary = rule.encode(), owner = "neo")
            smart.sync(server, c)
            assertEquals(listOf(1), added.single().seriesIds)
            assertEquals(listOf(4), removed.single().seriesIdsToRemove)
        }

    @Test
    fun syncNeverEmptiesACollectionWhenNothingMatches() =
        runTest {
            matches = emptyList()
            smart.sync(server, CollectionDto(9, "Isekai picks", summary = rule.encode(), owner = "neo"))
            assertEquals(listOf(), added)
            assertEquals(listOf(), removed)
        }
}

private fun unusedLists(): ListsRepository =
    java.lang.reflect.Proxy.newProxyInstance(
        ListsRepository::class.java.classLoader,
        arrayOf(ListsRepository::class.java),
    ) { _, m, _ ->
        error("unused: ${m.name}")
    } as ListsRepository
