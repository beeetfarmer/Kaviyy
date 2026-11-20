package com.nl.kaviyy.core.data.di

import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun session(repository: SessionRepository): AuthSession
}
