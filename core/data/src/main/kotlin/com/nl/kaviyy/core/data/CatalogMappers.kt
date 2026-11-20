package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.database.VolumeEntity
import com.nl.kaviyy.core.network.ChapterDto
import com.nl.kaviyy.core.network.LibraryDto
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.VolumeDto
import com.nl.kaviyy.core.network.kavitaTime

internal fun LibraryDto.toEntity(serverId: Long) =
    LibraryEntity(serverId, id, name.orEmpty(), type, kavitaTime(lastScanned))

internal fun SeriesDto.toEntity(serverId: Long) =
    SeriesEntity(
        serverId = serverId,
        id = id,
        libraryId = libraryId,
        name = name.orEmpty(),
        sortName = sortName ?: name.orEmpty(),
        localizedName = localizedName?.takeIf { it.isNotBlank() },
        format = format,
        pages = pages,
        pagesRead = pagesRead,
        wordCount = wordCount,
        userRating = userRating,
        primaryColor = primaryColor,
        created = kavitaTime(created),
        lastChapterAdded = kavitaTime(lastChapterAddedUtc),
        latestReadDate = kavitaTime(latestReadDate),
    )

internal fun VolumeDto.toEntity(serverId: Long) =
    VolumeEntity(
        serverId,
        id,
        seriesId,
        name.orEmpty(),
        minNumber,
        maxNumber,
        pages,
        pagesRead,
        kavitaTime(lastModifiedUtc),
    )

internal fun ChapterDto.toEntity(
    serverId: Long,
    seriesId: Int,
) = ChapterEntity(
    serverId = serverId,
    id = id,
    volumeId = volumeId,
    seriesId = seriesId,
    range = range,
    title = title,
    titleName = titleName?.takeIf { it.isNotBlank() },
    minNumber = minNumber,
    sortOrder = sortOrder,
    pages = pages,
    pagesRead = pagesRead,
    isSpecial = isSpecial,
    format = format,
    wordCount = wordCount,
    fileBytes = files.sumOf { it.bytes },
    releaseDate = kavitaTime(releaseDate),
    lastModified = kavitaTime(lastModifiedUtc),
    volumeTitle = volumeTitle?.takeIf { it.isNotBlank() },
)
