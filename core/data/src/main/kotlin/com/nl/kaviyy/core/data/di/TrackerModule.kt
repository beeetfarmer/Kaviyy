package com.nl.kaviyy.core.data.di

import com.nl.kaviyy.core.data.ScrobbleSignal
import com.nl.kaviyy.core.data.TrackerApis
import com.nl.kaviyy.core.data.TrackerSync
import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import com.nl.kaviyy.core.network.tracker.AniListApi
import com.nl.kaviyy.core.network.tracker.KitsuApi
import com.nl.kaviyy.core.network.tracker.MalApi
import com.nl.kaviyy.core.network.tracker.trackerHttpClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object TrackerModule {
    @Provides
    @Singleton
    fun trackerApis(
        base: OkHttpClient,
        @Dispatcher(KaviyyDispatchers.IO) io: CoroutineDispatcher,
    ): TrackerApis {
        val http = trackerHttpClient(base)
        return TrackerApis(AniListApi(http, io), MalApi(http, io), KitsuApi(http, io))
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface TrackerBindings {
    @Binds
    fun scrobbleSignal(sync: TrackerSync): ScrobbleSignal
}
