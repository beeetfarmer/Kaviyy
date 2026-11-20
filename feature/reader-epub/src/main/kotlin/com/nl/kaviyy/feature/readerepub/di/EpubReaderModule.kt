package com.nl.kaviyy.feature.readerepub.di

import com.nl.kaviyy.feature.readerepub.PublicationLoader
import com.nl.kaviyy.feature.readerepub.ReadiumPublicationLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface EpubReaderModule {
    @Binds
    fun publications(loader: ReadiumPublicationLoader): PublicationLoader
}
