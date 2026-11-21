package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.server.ServerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Which series list a filter belongs to: the Library grid or the Want to Read list. */
enum class FilterScope { Library, WantToRead }

/**
 * The current filter per server and [FilterScope], shared by a list's screen and the filter sheet (different feature
 * modules). In memory; the Library screen saves its filter in its SavedStateHandle for process death.
 */
@Singleton
class LibraryFilterStore
    @Inject
    constructor() {
        private val filters = MutableStateFlow<Map<Pair<ServerId, FilterScope>, SeriesFilter>>(emptyMap())

        fun filter(
            server: ServerId,
            scope: FilterScope = FilterScope.Library,
        ): Flow<SeriesFilter> = filters.map { it[server to scope] ?: SeriesFilter() }.distinctUntilChanged()

        fun current(
            server: ServerId,
            scope: FilterScope = FilterScope.Library,
        ): SeriesFilter = filters.value[server to scope] ?: SeriesFilter()

        fun update(
            server: ServerId,
            scope: FilterScope = FilterScope.Library,
            transform: (SeriesFilter) -> SeriesFilter,
        ) = filters.update { it + ((server to scope) to transform(it[server to scope] ?: SeriesFilter())) }

        /** Seeds a restored Library filter unless one is already set this process. */
        fun restore(
            server: ServerId,
            filter: SeriesFilter,
        ) = filters.update {
            val key = server to FilterScope.Library
            if (key in it) it else it + (key to filter)
        }
    }
