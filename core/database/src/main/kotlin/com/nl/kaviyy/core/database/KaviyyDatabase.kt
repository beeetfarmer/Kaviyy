package com.nl.kaviyy.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ServerEntity::class,
        LibraryEntity::class,
        SeriesEntity::class,
        VolumeEntity::class,
        ChapterEntity::class,
        ProgressEntity::class,
        OutboxEntity::class,
        DownloadEntity::class,
        BookmarkEntity::class,
        AnnotationEntity::class,
        CollectionEntity::class,
        CollectionSeriesEntity::class,
        ReadingListEntity::class,
        ReadingListItemEntity::class,
        SmartFilterEntity::class,
        DashboardStreamEntity::class,
        DashboardItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class KaviyyDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao

    abstract fun libraryDao(): LibraryDao

    abstract fun seriesDao(): SeriesDao
}
