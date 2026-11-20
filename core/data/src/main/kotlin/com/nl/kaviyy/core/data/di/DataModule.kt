package com.nl.kaviyy.core.data.di

import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ConnectivityNetworkMonitor
import com.nl.kaviyy.core.data.HomeRepository
import com.nl.kaviyy.core.data.NetworkMonitor
import com.nl.kaviyy.core.data.OfflineFirstHomeRepository
import com.nl.kaviyy.core.data.ServerApis
import com.nl.kaviyy.core.data.SessionRepository
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
    fun network(monitor: ConnectivityNetworkMonitor): NetworkMonitor

    companion object {
        @Provides
        fun serverApis(
            apis: KavitaApis,
            session: AuthSession,
        ): ServerApis = ServerApis { server -> apis.forServer(server, session.credentialSource(server.id)) }
    }
}
