package com.nl.kaviyy.core.sync

import com.nl.kaviyy.core.model.server.Server
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow

/** What screens need from sync: how many changes are waiting, and a way to push them now. */
interface SyncStatus {
    fun pending(server: Server): Flow<Int>

    /** Flushes as soon as there is a network. */
    fun syncNow()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface SyncModule {
    @Binds
    fun status(sync: Sync): SyncStatus
}
