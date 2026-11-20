package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.server.ServerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Library grid's current filter per server, shared by the Library chips and the filter sheet (different feature
 * modules). In memory; the Library screen saves it in its SavedStateHandle for process death.
 */
@Singleton
class LibraryFilterStore
    @Inject
    constructor() {
        private val filters = MutableStateFlow<Map<ServerId, SeriesFilter>>(emptyMap())

        fun filter(server: ServerId): Flow<SeriesFilter> =
            filters.map { it[server] ?: SeriesFilter() }.distinctUntilChanged()

        fun current(server: ServerId): SeriesFilter = filters.value[server] ?: SeriesFilter()

        fun update(
            server: ServerId,
            transform: (SeriesFilter) -> SeriesFilter,
        ) = filters.update { it + (server to transform(it[server] ?: SeriesFilter())) }

        /** Seeds a restored filter unless one is already set this process. */
        fun restore(
            server: ServerId,
            filter: SeriesFilter,
        ) = filters.update { if (server in it) it else it + (server to filter) }
    }
