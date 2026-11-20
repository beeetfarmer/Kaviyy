package com.nl.kaviyy.core.data.di

import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.ConnectivityNetworkMonitor
import com.nl.kaviyy.core.data.HomeRepository
import com.nl.kaviyy.core.data.KavitaReviewRepository
import com.nl.kaviyy.core.data.KavitaSmartFilterRepository
import com.nl.kaviyy.core.data.NetworkMonitor
import com.nl.kaviyy.core.data.OfflineFirstCatalogRepository
import com.nl.kaviyy.core.data.OfflineFirstHomeRepository
import com.nl.kaviyy.core.data.OfflineFirstReadStateRepository
import com.nl.kaviyy.core.data.ReadStateRepository
import com.nl.kaviyy.core.data.ReviewRepository
import com.nl.kaviyy.core.data.ServerApis
import com.nl.kaviyy.core.data.SessionRepository
import com.nl.kaviyy.core.data.SmartFilterRepository
import com.nl.kaviyy.core.network.KavitaApis
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun session(repository: SessionRepository): AuthSession

    @Binds
    fun home(repository: OfflineFirstHomeRepository): HomeRepository

    @Binds
    fun catalog(repository: OfflineFirstCatalogRepository): CatalogRepository

    @Binds
    fun reviews(repository: KavitaReviewRepository): ReviewRepository

    @Binds
    fun readState(repository: OfflineFirstReadStateRepository): ReadStateRepository

    @Binds
    fun smartFilters(repository: KavitaSmartFilterRepository): SmartFilterRepository

    @Binds
    fun network(monitor: ConnectivityNetworkMonitor): NetworkMonitor

    companion object {
        @Provides
        fun serverApis(
            apis: KavitaApis,
            session: AuthSession,
        ): ServerApis = ServerApis { server -> apis.forServer(server, session.credentialSource(server.id)) }
    }
}
