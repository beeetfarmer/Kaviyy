package com.nl.kaviyy.core.database.di

import android.content.Context
import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.LibraryDao
import com.nl.kaviyy.core.database.SeriesDao
import com.nl.kaviyy.core.database.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): KaviyyDatabase = Room.databaseBuilder(context, KaviyyDatabase::class.java, "kaviyy.db").build()

    @Provides
    fun serverDao(db: KaviyyDatabase): ServerDao = db.serverDao()

    @Provides
    fun libraryDao(db: KaviyyDatabase): LibraryDao = db.libraryDao()

    @Provides
    fun seriesDao(db: KaviyyDatabase): SeriesDao = db.seriesDao()
}
