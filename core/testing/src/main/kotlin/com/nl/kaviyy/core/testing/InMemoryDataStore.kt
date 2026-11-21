package com.nl.kaviyy.core.testing

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A DataStore held in memory, for tests. */
class InMemoryDataStore<T>(
    initial: T,
) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<T> = state

    override suspend fun updateData(transform: suspend (t: T) -> T): T =
        transform(state.value).also { state.value = it }
}
