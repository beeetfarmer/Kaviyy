package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.server.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Library's view of the Sensitive Content settings: covers to blur and series to leave out. */
data class Sensitive(
    val blurIds: Set<Int> = emptySet(),
    /** 0 while blur is switched off. */
    val blurRadius: Int = 0,
    val hiddenIds: Set<Int> = emptySet(),
) {
    fun blurOf(seriesId: Int): Int = if (seriesId in blurIds) blurRadius else 0
}

/** Interface = test seam. */
fun interface SensitiveSource {
    fun library(server: Server): Flow<Sensitive>
}

/** Tag matches come from Home's refresh (cached in Room), exceptions and strength from the settings. */
class TaggedSensitiveSource
    @Inject
    constructor(
        private val home: HomeRepository,
        private val settings: AppSettingsStore,
    ) : SensitiveSource {
        override fun library(server: Server): Flow<Sensitive> =
            combine(home.home(server), settings.settings) { h, a ->
                Sensitive(h.blurIds, a.activeBlurRadius, h.hiddenIds(a, server.id.value, library = true))
            }.distinctUntilChanged()
    }
