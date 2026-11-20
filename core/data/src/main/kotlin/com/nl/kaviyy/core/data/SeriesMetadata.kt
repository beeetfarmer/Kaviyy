package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.SeriesMetadataEntity
import com.nl.kaviyy.core.network.SeriesMetadataDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A genre, tag or person: [id] for browsing, [name] for display. [role] = Kavita `PersonRole` (people only). */
@Serializable
data class NamedRef(
    val id: Int,
    val name: String,
    val role: Int = 0,
)

/** Kavita `PersonRole` 3–15 in order, as filter fields (same order as the enum). */
private val PERSON_FIELDS =
    listOf(
        FilterField.Writers,
        FilterField.Penciller,
        FilterField.Inker,
        FilterField.Colorist,
        FilterField.Letterer,
        FilterField.CoverArtist,
        FilterField.Editor,
        FilterField.Publisher,
        FilterField.Characters,
        FilterField.Translators,
        FilterField.Imprint,
        FilterField.Team,
        FilterField.Location,
    )
private const val FIRST_ROLE = 3
const val ROLE_WRITER = FIRST_ROLE

/** The filter field that finds a person's series in [role]; null for an unknown role. */
fun personField(role: Int): FilterField? = PERSON_FIELDS.getOrNull(role - FIRST_ROLE)

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
    /** Everyone credited, in role order; see [writers]. */
    val people: List<NamedRef> = emptyList(),
    val webLinks: List<String> = emptyList(),
    val hoursLeft: Float = 0f,
    val wantToRead: Boolean = false,
    val continueChapterId: Int? = null,
) {
    val writers: List<NamedRef> get() = people.filter { it.role == ROLE_WRITER }
}

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
    // All roles live in writersJson (role-tagged), so no schema change was needed.
    writersJson =
        listOf(
            writers,
            pencillers,
            inkers,
            colorists,
            letterers,
            coverArtists,
            editors,
            publishers,
            characters,
            translators,
            imprints,
            teams,
            locations,
        ).flatMapIndexed { i, list -> list.mapNotNull { p -> p.name?.let { NamedRef(p.id, it, FIRST_ROLE + i) } } }
            .json(),
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
        // Rows cached before roles were stored have role 0: they were all writers.
        people = writersJson.refs().map { if (it.role == 0) it.copy(role = ROLE_WRITER) else it },
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
