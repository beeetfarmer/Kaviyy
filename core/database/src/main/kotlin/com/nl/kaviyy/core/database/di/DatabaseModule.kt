package com.nl.kaviyy.core.database.di

import android.content.Context
import androidx.room.Room
import com.nl.kaviyy.core.database.AnnotationDao
import com.nl.kaviyy.core.database.BookmarkDao
import com.nl.kaviyy.core.database.CollectionDao
import com.nl.kaviyy.core.database.DashboardDao
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.LibraryDao
import com.nl.kaviyy.core.database.OutboxDao
import com.nl.kaviyy.core.database.ProgressDao
import com.nl.kaviyy.core.database.ReadingListDao
import com.nl.kaviyy.core.database.ReadingProfileDao
import com.nl.kaviyy.core.database.RecentSearchDao
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

    @Provides
    fun readingProfileDao(db: KaviyyDatabase): ReadingProfileDao = db.readingProfileDao()

    @Provides
    fun recentSearchDao(db: KaviyyDatabase): RecentSearchDao = db.recentSearchDao()

    @Provides
    fun outboxDao(db: KaviyyDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun bookmarkDao(db: KaviyyDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun progressDao(db: KaviyyDatabase): ProgressDao = db.progressDao()

    @Provides
    fun annotationDao(db: KaviyyDatabase): AnnotationDao = db.annotationDao()

    @Provides
    fun collectionDao(db: KaviyyDatabase): CollectionDao = db.collectionDao()

    @Provides
    fun readingListDao(db: KaviyyDatabase): ReadingListDao = db.readingListDao()

    @Provides
    fun dashboardDao(db: KaviyyDatabase): DashboardDao = db.dashboardDao()
}
