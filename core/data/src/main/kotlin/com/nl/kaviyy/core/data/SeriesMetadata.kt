package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.SeriesMetadataEntity
import com.nl.kaviyy.core.network.SeriesMetadataDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A genre, tag or person: [id] for browsing, [name] for display. */
@Serializable
data class NamedRef(
    val id: Int,
    val name: String,
)

/** Series detail extras (screen 04). [summary] is raw server HTML: sanitize before display. */
data class SeriesMetadata(
    val summary: String? = null,
    val releaseYear: Int = 0,
    /** Kavita `AgeRating`. */
    val ageRating: Int = 0,
    /** Kavita `PublicationStatus`. */
    val publicationStatus: Int = 0,
    val genres: List<NamedRef> = emptyList(),
    val tags: List<NamedRef> = emptyList(),
    val writers: List<NamedRef> = emptyList(),
    val webLinks: List<String> = emptyList(),
    val hoursLeft: Float = 0f,
    val wantToRead: Boolean = false,
    val continueChapterId: Int? = null,
)

private val json = Json { ignoreUnknownKeys = true }
private val refs = ListSerializer(NamedRef.serializer())

private fun String.refs(): List<NamedRef> = runCatching { json.decodeFromString(refs, this) }.getOrDefault(emptyList())

private fun List<NamedRef>.json(): String = json.encodeToString(refs, this)

internal fun SeriesMetadataDto.toEntity(
    serverId: Long,
    seriesId: Int,
    hoursLeft: Float,
    wantToRead: Boolean,
    continueChapterId: Int?,
) = SeriesMetadataEntity(
    serverId = serverId,
    seriesId = seriesId,
    summary = summary,
    releaseYear = releaseYear,
    ageRating = ageRating,
    publicationStatus = publicationStatus,
    genresJson = genres.mapNotNull { g -> g.title?.let { NamedRef(g.id, it) } }.json(),
    tagsJson = tags.mapNotNull { t -> t.title?.let { NamedRef(t.id, it) } }.json(),
    writersJson = writers.mapNotNull { p -> p.name?.let { NamedRef(p.id, it) } }.json(),
    webLinks = webLinks,
    hoursLeft = hoursLeft,
    wantToRead = wantToRead,
    continueChapterId = continueChapterId,
)

internal fun SeriesMetadataEntity.toDomain() =
    SeriesMetadata(
        summary = summary,
        releaseYear = releaseYear,
        ageRating = ageRating,
        publicationStatus = publicationStatus,
        genres = genresJson.refs(),
        tags = tagsJson.refs(),
        writers = writersJson.refs(),
        webLinks =
            webLinks
                .orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() },
        hoursLeft = hoursLeft,
        wantToRead = wantToRead,
        continueChapterId = continueChapterId,
    )
