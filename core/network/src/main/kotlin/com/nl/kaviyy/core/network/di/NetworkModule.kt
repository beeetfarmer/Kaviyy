package com.nl.kaviyy.core.network.di

import com.nl.kaviyy.core.network.ServerProbe
import com.nl.kaviyy.core.network.ServerProber
import com.nl.kaviyy.core.network.baseHttpClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = baseHttpClient()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkBindings {
    @Binds
    fun prober(probe: ServerProbe): ServerProber
}
