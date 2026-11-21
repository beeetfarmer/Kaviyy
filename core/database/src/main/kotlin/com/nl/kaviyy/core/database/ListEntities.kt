package com.nl.kaviyy.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

private const val SERVER = "serverId"

@Entity(
    tableName = "collections",
    primaryKeys = [SERVER, "id"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
)
data class CollectionEntity(
    val serverId: Long,
    val id: Int,
    val title: String,
    val summary: String? = null,
    val promoted: Boolean = false,
    val itemCount: Int = 0,
    val owner: String? = null,
    /** Kavita `ScrobbleProvider`: 0 = made in Kavita; others are synced from an external list ("Smart"). */
    @ColumnInfo(defaultValue = "0") val source: Int = 0,
    /** Server-side position (most recently changed first). */
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

@Entity(
    tableName = "collection_series",
    primaryKeys = [SERVER, "collectionId", "seriesId"],
    foreignKeys = [
        ForeignKey(CollectionEntity::class, [SERVER, "id"], [SERVER, "collectionId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class CollectionSeriesEntity(
    val serverId: Long,
    val collectionId: Int,
    val seriesId: Int,
    val position: Int,
)

@Entity(
    tableName = "reading_lists",
    primaryKeys = [SERVER, "id"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
)
data class ReadingListEntity(
    val serverId: Long,
    val id: Int,
    val title: String,
    val summary: String? = null,
    val promoted: Boolean = false,
    val itemCount: Int = 0,
    val startingYear: Int = 0,
    val startingMonth: Int = 0,
    val endingYear: Int = 0,
    val endingMonth: Int = 0,
)

@Entity(
    tableName = "reading_list_items",
    primaryKeys = [SERVER, "id"],
    foreignKeys = [
        ForeignKey(ReadingListEntity::class, [SERVER, "id"], [SERVER, "readingListId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(SERVER, "readingListId")],
)
data class ReadingListItemEntity(
    val serverId: Long,
    val id: Int,
    val readingListId: Int,
    val position: Int,
    val chapterId: Int,
    val volumeId: Int,
    val seriesId: Int,
    val title: String? = null,
    val pagesRead: Int = 0,
    val pagesTotal: Int = 0,
)

@Entity(
    tableName = "smart_filters",
    primaryKeys = [SERVER, "id"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
)
data class SmartFilterEntity(
    val serverId: Long,
    val id: Int,
    val name: String,
    /** Kavita's encoded filter string. */
    val filter: String,
    /** Kavita `FilterEntityType`. */
    val entityType: Int,
)

/** Home streams (On Deck, Recently Updated, smart filters…) in the user's order. */
@Entity(
    tableName = "dashboard_streams",
    primaryKeys = [SERVER, "id"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
)
data class DashboardStreamEntity(
    val serverId: Long,
    val id: Int,
    val name: String,
    val position: Int,
    /** Kavita `DashboardStreamType`. */
    val streamType: Int,
    val visible: Boolean,
    val smartFilterEncoded: String? = null,
)

/** Cached contents of a dashboard stream (series ids in display order) for offline Home. */
@Entity(
    tableName = "dashboard_items",
    primaryKeys = [SERVER, "streamId", "position"],
    foreignKeys = [
        ForeignKey(DashboardStreamEntity::class, [SERVER, "id"], [SERVER, "streamId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class DashboardItemEntity(
    val serverId: Long,
    val streamId: Int,
    val position: Int,
    val seriesId: Int,
    /** Chapter to resume (On Deck / Continue Reading) or the updated chapter (Recently Updated). */
    val chapterId: Int? = null,
    /** Recently Updated: number of new chapters. */
    @ColumnInfo(defaultValue = "0") val count: Int = 0,
)
