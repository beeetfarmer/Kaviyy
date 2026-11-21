package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.CollectionAddDto
import com.nl.kaviyy.core.network.CollectionDto
import com.nl.kaviyy.core.network.CollectionRemoveDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.apiCall
import javax.inject.Inject

/**
 * A smart collection's rule: series with any (or all) of these tags and genres. Kavita has no rule-based
 * collections, so the rule rides in the collection's summary as one marker line (see [encode]); that syncs it to
 * the server and every device, and the app keeps the members in step.
 */
data class SmartRule(
    val matchAll: Boolean,
    val tags: List<RuleValue>,
    val genres: List<RuleValue>,
) {
    val isEmpty: Boolean get() = tags.isEmpty() && genres.isEmpty()

    /**
     * Any: one statement per field, combined with OR (Contains = any of the values).
     * All: MustContains per field, combined with AND.
     */
    fun filter(): SeriesFilter {
        val comparison = if (matchAll) Comparison.MustContains else Comparison.Contains
        return SeriesFilter(
            matchAll = matchAll,
            rules =
                listOfNotNull(
                    tags.takeIf { it.isNotEmpty() }?.let { FilterRule(FilterField.Tags, comparison, it) },
                    genres.takeIf { it.isNotEmpty() }?.let { FilterRule(FilterField.Genres, comparison, it) },
                ),
        )
    }

    /** `[smart-list match=any tags=12:Isekai,40:Time Travel genres=3:Fantasy]`: ids for matching, names to show. */
    fun encode(): String =
        buildString {
            append("[smart-list match=").append(if (matchAll) "all" else "any")
            if (tags.isNotEmpty()) append(" tags=").append(tags.encodeValues())
            if (genres.isNotEmpty()) append(" genres=").append(genres.encodeValues())
            append(']')
        }

    companion object {
        private val MARKER = Regex("""\[smart-list match=(any|all)(?: tags=([^\]]*?))?(?: genres=([^\]]*?))?]""")

        /** The rule in a collection [summary], or null for an ordinary collection. */
        fun from(summary: String?): SmartRule? {
            val m = summary?.let { MARKER.find(it) } ?: return null
            val (match, tags, genres) = m.destructured
            return SmartRule(match == "all", decodeValues(tags), decodeValues(genres)).takeUnless { it.isEmpty }
        }

        /** [summary] without the marker line: what the user wrote. */
        fun userSummary(summary: String?): String? = summary?.replace(MARKER, "")?.trim()?.ifEmpty { null }

        /** [userText] with the rule appended on its own line. */
        fun summaryWith(
            userText: String?,
            rule: SmartRule?,
        ): String? = listOfNotNull(userSummary(userText), rule?.encode()).joinToString("\n\n").ifEmpty { null }

        // Names may not contain the separators; they're only labels (ids do the matching).
        private fun List<RuleValue>.encodeValues() =
            joinToString(",") { v -> v.code + ":" + v.label.replace(Regex("[,:\\]\\[=]"), " ").trim() }

        private fun decodeValues(text: String): List<RuleValue> =
            text.split(',').mapNotNull { part ->
                val code = part.substringBefore(':').trim().takeIf { c -> c.isNotEmpty() && c.all(Char::isDigit) }
                code?.let { RuleValue(it, part.substringAfter(':', it).trim().ifEmpty { it }) }
            }
    }
}

/** Creates smart collections and keeps their series in step with their rules. Online only. */
class SmartCollections
    @Inject
    constructor(
        private val apis: ServerApis,
        private val lists: ListsRepository,
    ) {
        /** How many series the rule matches now (for the create sheet's live count). */
        suspend fun count(
            server: Server,
            rule: SmartRule,
        ): AppResult<Int> = apiCall { matching(apis.api(server), rule).size }

        /**
         * Kavita creates a collection by adding series to a new one, so at least one series must match. Returns
         * the new collection's id.
         */
        suspend fun create(
            server: Server,
            title: String,
            rule: SmartRule,
        ): AppResult<Int> {
            val name = title.trim()
            val result =
                apiCall {
                    val api = apis.api(server)
                    val ids = matching(api, rule)
                    if (ids.isEmpty()) return@apiCall null
                    api.addToCollection(CollectionAddDto(0, name, ids.toList()))
                    // The add returns nothing: find the new one (ours, that name, newest).
                    val created =
                        api
                            .collections()
                            .filter { it.title == name && it.owner.equals(server.username, ignoreCase = true) }
                            .maxByOrNull { it.id } ?: return@apiCall null
                    api.updateCollection(created.copy(summary = SmartRule.summaryWith(created.summary, rule)))
                    created.id
                }
            lists.refreshCollections(server)
            return when (result) {
                is AppResult.Success -> {
                    result.value?.let { AppResult.Success(it) }
                        ?: AppResult.Failure(AppError.NotFound)
                }

                is AppResult.Failure -> {
                    result
                }
            }
        }

        /** Re-applies [collection]'s rule: adds newly matching series, removes ones that no longer match. */
        suspend fun sync(
            server: Server,
            collection: CollectionDto,
        ): AppResult<Unit> {
            val rule = SmartRule.from(collection.summary) ?: return AppResult.Success(Unit)
            return apiCall { syncWith(apis.api(server), collection, rule) }
        }

        /** "Update now" on a collection screen: re-applies the rule of collection [id]. */
        suspend fun sync(
            server: Server,
            id: Int,
        ): AppResult<Unit> {
            val found = apiCall { apis.api(server).collections().firstOrNull { it.id == id } }
            val result =
                when (found) {
                    is AppResult.Success -> {
                        found.value?.let { sync(server, it) }
                            ?: AppResult.Failure(AppError.NotFound)
                    }

                    is AppResult.Failure -> {
                        found
                    }
                }
            return result.also { lists.refreshCollections(server) }
        }

        /** Every smart collection the user owns on [server]; failures are skipped (next sync retries). */
        suspend fun syncAll(server: Server) {
            val owned =
                (apiCall { apis.api(server).collections() } as? AppResult.Success)?.value.orEmpty().filter {
                    it.source == 0 && it.owner.equals(server.username, ignoreCase = true) &&
                        SmartRule.from(it.summary) != null
                }
            owned.forEach { sync(server, it) }
            if (owned.isNotEmpty()) lists.refreshCollections(server)
        }

        private suspend fun syncWith(
            api: KavitaApi,
            collection: CollectionDto,
            rule: SmartRule,
        ) {
            val target = matching(api, rule)
            val current = seriesIds(api, collectionQuery(collection.id).filter)
            val add = target - current
            // An empty rule result would empty (and so delete) the collection: keep it until something matches.
            val remove = if (target.isEmpty()) emptySet() else current - target
            if (add.isNotEmpty()) {
                api.addToCollection(
                    CollectionAddDto(collection.id, collection.title.orEmpty(), add.toList()),
                )
            }
            if (remove.isNotEmpty()) api.removeFromCollection(CollectionRemoveDto(collection, remove.toList()))
        }

        private suspend fun matching(
            api: KavitaApi,
            rule: SmartRule,
        ): Set<Int> = if (rule.isEmpty) emptySet() else seriesIds(api, rule.filter())

        /** Every series id the filter matches, page by page. */
        private suspend fun seriesIds(
            api: KavitaApi,
            filter: SeriesFilter,
        ): Set<Int> {
            val dto = filter.toDto(emptyList())
            val ids = mutableSetOf<Int>()
            var page = 1
            while (ids.size < MAX_SERIES) {
                val batch = api.series(dto, page, PAGE_SIZE)
                batch.mapTo(ids) { it.id }
                if (batch.size < PAGE_SIZE) break
                page++
            }
            return ids
        }
    }

private const val PAGE_SIZE = 100

// ponytail: a rule matching more than this many series is capped; raise it (or page lazily) if libraries are huge.
private const val MAX_SERIES = 5_000
