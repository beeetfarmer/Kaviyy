package com.nl.kaviyy.core.download.di

import com.nl.kaviyy.core.download.DownloadControls
import com.nl.kaviyy.core.download.DownloadQueue
import com.nl.kaviyy.core.download.Downloads
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DownloadModule {
    @Binds
    fun queue(downloads: Downloads): DownloadQueue

    @Binds
    fun controls(downloads: Downloads): DownloadControls
}
