package com.nl.kaviyy.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

private const val SERVER = "serverId"

/** Local read position per chapter: written first (offline-first), then pushed via [OutboxEntity]. */
@Entity(
    tableName = "progress",
    primaryKeys = [SERVER, "chapterId"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
    indices = [Index(SERVER, "seriesId")],
)
data class ProgressEntity(
    val serverId: Long,
    val chapterId: Int,
    val volumeId: Int,
    val seriesId: Int,
    val libraryId: Int,
    val pageNum: Int,
    /** EPUB position (Kavita `bookScrollId`). */
    val bookScrollId: String? = null,
    /** Server-clock time of this position (newest wins; corrected with the server `Date` offset). */
    val lastModifiedUtc: Long,
)

/**
 * Pending change for the server. [entityKey] identifies what it is about (e.g. a chapter id) so the sync worker can
 * coalesce to the latest event per (type, key); [clientUuid] makes creates/deletes idempotent on retry.
 */
@Entity(
    tableName = "outbox",
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
    indices = [Index(SERVER, "type", "entityKey"), Index(value = ["clientUuid"], unique = true)],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    /** e.g. "progress", "markRead", "wantToRead", "rating", "bookmark", "annotation". */
    val type: String,
    val entityKey: String,
    /** Request body as JSON. */
    val payload: String,
    val clientUuid: String,
    val createdAt: Long,
    val attempts: Int = 0,
)

/** One downloaded (or queued) chapter. Files live at `filesDir/downloads/{serverId}/{seriesId}/{chapterId}/`. */
@Entity(
    tableName = "downloads",
    primaryKeys = [SERVER, "chapterId"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
    indices = [Index(SERVER, "seriesId"), Index("state")],
)
data class DownloadEntity(
    val serverId: Long,
    val chapterId: Int,
    val seriesId: Int,
    val volumeId: Int,
    /** QUEUED, RUNNING, PAUSED, FAILED, COMPLETED (state machine in core:download). */
    val state: String,
    /** True: page images; false: the original EPUB/PDF file. */
    val pageImages: Boolean,
    val pagesDone: Int = 0,
    val pagesTotal: Int = 0,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val error: String? = null,
    /** Chapter `lastModified` when downloaded: newer on the server = "Update available". */
    val serverLastModified: Long = 0,
    val updatedAt: Long = 0,
)

/** Page bookmark. Natural key so it can be created offline; [remoteId] is set once the server knows it. */
@Entity(
    tableName = "bookmarks",
    primaryKeys = [SERVER, "chapterId", "page"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
    indices = [Index(SERVER, "seriesId")],
)
data class BookmarkEntity(
    val serverId: Long,
    val chapterId: Int,
    val page: Int,
    val seriesId: Int,
    val volumeId: Int,
    val imageOffset: Int = 0,
    val remoteId: Int? = null,
)

/** EPUB highlight / note, keyed by a client UUID so it can be created offline. */
@Entity(
    tableName = "annotations",
    primaryKeys = [SERVER, "clientUuid"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
    indices = [Index(SERVER, "chapterId"), Index(SERVER, "remoteId")],
)
data class AnnotationEntity(
    val serverId: Long,
    val clientUuid: String,
    val remoteId: Int? = null,
    val chapterId: Int,
    val volumeId: Int,
    val seriesId: Int,
    val pageNumber: Int,
    val xPath: String,
    val endingXPath: String,
    val selectedText: String,
    val comment: String? = null,
    /** Highlight colour slot. */
    val selectedSlotIndex: Int = 0,
    val containsSpoiler: Boolean = false,
    val createdUtc: Long,
    val lastModifiedUtc: Long,
)
