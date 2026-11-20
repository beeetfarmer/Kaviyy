package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.DecodeFilterDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.apiCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject

/** A saved series smart filter. [encoded] is Kavita's encoded filter string. */
data class SavedFilter(
    val id: Int,
    val name: String,
    val encoded: String,
)

/** Where a saved filter appears (DESIGN.md screen 09 switches). */
data class FilterPlacement(
    val onHome: Boolean = false,
    val inLibrary: Boolean = false,
)

/** Smart filters on the server (network only): list, load for editing, save with placement, delete. */
interface SmartFilterRepository {
    suspend fun list(server: Server): AppResult<List<SavedFilter>>

    /** Pinned ("Pin in Library") filters, in side-nav order. */
    suspend fun pinned(server: Server): AppResult<List<SavedFilter>>

    /** Decodes a saved filter into rules, with option labels resolved. */
    suspend fun load(
        server: Server,
        saved: SavedFilter,
    ): AppResult<SeriesFilter>

    suspend fun placement(
        server: Server,
        filterId: Int,
    ): AppResult<FilterPlacement>

    /** Creates or updates by name, then adds/removes its Home and Library streams. */
    suspend fun save(
        server: Server,
        name: String,
        filter: SeriesFilter,
        scope: List<Int>,
        placement: FilterPlacement,
    ): AppResult<SavedFilter>

    suspend fun delete(
        server: Server,
        filterId: Int,
    ): AppResult<Unit>
}

private const val ENTITY_SERIES = 0

class KavitaSmartFilterRepository
    @Inject
    constructor(
        private val apis: ServerApis,
        private val catalog: CatalogRepository,
    ) : SmartFilterRepository {
        override suspend fun list(server: Server): AppResult<List<SavedFilter>> =
            apiCall { apis.api(server).smartFilters() }.map { list ->
                list.filter { it.entityType == ENTITY_SERIES }.mapNotNull { f ->
                    val name = f.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    SavedFilter(f.id, name, f.filter ?: return@mapNotNull null)
                }
            }

        override suspend fun pinned(server: Server): AppResult<List<SavedFilter>> {
            val api = apis.api(server)
            val nav = apiCall { api.sideNav(visibleOnly = true) }
            val all = list(server)
            return when {
                nav is AppResult.Failure -> {
                    nav
                }

                all is AppResult.Failure -> {
                    all
                }

                else -> {
                    val byId = all.getOrNull().orEmpty().associateBy { it.id }
                    AppResult.Success(nav.getOrNull().orEmpty().mapNotNull { s -> s.smartFilterId?.let(byId::get) })
                }
            }
        }

        override suspend fun load(
            server: Server,
            saved: SavedFilter,
        ): AppResult<SeriesFilter> {
            val decoded = apiCall { apis.api(server).decodeFilter(DecodeFilterDto(saved.encoded)) }
            val raw = decoded.getOrNull() ?: return decoded.map { SeriesFilter() }
            val filter = raw.toSeriesFilter()
            return AppResult.Success(withLabels(server, filter))
        }

        override suspend fun placement(
            server: Server,
            filterId: Int,
        ): AppResult<FilterPlacement> {
            val api = apis.api(server)
            val home = apiCall { api.dashboard(visibleOnly = false) }
            val nav = apiCall { api.sideNav(visibleOnly = false) }
            return when {
                home is AppResult.Failure -> {
                    home
                }

                nav is AppResult.Failure -> {
                    nav
                }

                else -> {
                    AppResult.Success(
                        FilterPlacement(
                            onHome = home.getOrNull().orEmpty().any { it.smartFilterId == filterId && it.visible },
                            inLibrary = nav.getOrNull().orEmpty().any { it.smartFilterId == filterId && it.visible },
                        ),
                    )
                }
            }
        }

        override suspend fun save(
            server: Server,
            name: String,
            filter: SeriesFilter,
            scope: List<Int>,
            placement: FilterPlacement,
        ): AppResult<SavedFilter> {
            val api = apis.api(server)
            val existing =
                list(
                    server,
                ).getOrNull().orEmpty().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            val dto = filter.toDto(scope).copy(id = existing?.id ?: 0, name = name.trim())
            val saved = apiCall { api.saveSmartFilter(dto) }
            if (saved is AppResult.Failure) return saved
            val after = list(server)
            val created = after.getOrNull()?.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            return when {
                after is AppResult.Failure -> after
                created == null -> AppResult.Failure(AppError.Unknown)
                else -> syncPlacement(api, created.id, placement).map { created }
            }
        }

        override suspend fun delete(
            server: Server,
            filterId: Int,
        ): AppResult<Unit> = apiCall { apis.api(server).deleteSmartFilter(filterId) }

        /** Adds or removes the filter's Home/Library streams to match [want]. */
        private suspend fun syncPlacement(
            api: KavitaApi,
            filterId: Int,
            want: FilterPlacement,
        ): AppResult<Unit> =
            apiCall {
                val home = api.dashboard(visibleOnly = false).filter { it.smartFilterId == filterId }
                when {
                    want.onHome && home.isEmpty() -> api.addDashboardStream(filterId)
                    !want.onHome -> home.forEach { api.removeDashboardStream(it.id) }
                }
                val nav = api.sideNav(visibleOnly = false).filter { it.smartFilterId == filterId }
                when {
                    want.inLibrary && nav.isEmpty() -> api.addSideNavStream(filterId)
                    !want.inLibrary -> nav.forEach { api.removeSideNavStream(it.id) }
                }
            }

        /** Replaces option codes with names, one lookup per source; unknown codes keep the code. */
        private suspend fun withLabels(
            server: Server,
            filter: SeriesFilter,
        ): SeriesFilter {
            val sources =
                filter.rules
                    .mapNotNull { it.field.source }
                    .filter { it != OptionSource.Formats }
                    .distinct()
            val names =
                sources.associateWith { source ->
                    catalog.filterOptions(server, source, filter.libraryIds).getOrNull().orEmpty().associate {
                        it.code to
                            it.label
                    }
                }
            return filter.copy(
                rules =
                    filter.rules.map { rule ->
                        val table = rule.field.source?.let(names::get) ?: return@map rule
                        rule.copy(values = rule.values.map { v -> RuleValue(v.code, table[v.code] ?: v.label) })
                    },
            )
        }
    }

/** Kavita's decoded filter JSON → [SeriesFilter]. Unknown fields/comparisons are dropped. */
internal fun JsonObject.toSeriesFilter(): SeriesFilter {
    fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
    val statements = (this["statements"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val libraries =
        statements
            .filter { it.int("field") == FilterField.LIBRARIES_API }
            .flatMap { s ->
                (s["value"] as? JsonPrimitive)
                    ?.content
                    .orEmpty()
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
            }
    val rules =
        statements.mapNotNull { s ->
            val field = FilterField.entries.firstOrNull { it.api == s.int("field") } ?: return@mapNotNull null
            val comparison = Comparison.entries.firstOrNull { it.api == s.int("comparison") } ?: return@mapNotNull null
            val value = (s["value"] as? JsonPrimitive)?.content.orEmpty()
            val codes = if (field.source != null) value.split(",").map { it.trim() } else listOf(value)
            FilterRule(field, comparison, codes.filter { it.isNotEmpty() }.map { RuleValue(it) })
        }
    val sort = this["sortOptions"] as? JsonObject
    val sortField = sort?.int("sortField")
    val ascending = (sort?.get("isAscending") as? JsonPrimitive)?.booleanOrNull
    return SeriesFilter(
        matchAll = int("combination") != 0,
        rules = rules,
        sort =
            SeriesSort.entries.firstOrNull { it.field == sortField && it.ascending == ascending }
                ?: SeriesSort.entries.firstOrNull { it.field == sortField }
                ?: SeriesSort.RecentlyAdded,
        libraryIds = libraries.distinct(),
    )
}
