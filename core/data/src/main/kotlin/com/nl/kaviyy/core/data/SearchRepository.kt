package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.RecentSearchDao
import com.nl.kaviyy.core.database.RecentSearchEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.SearchResultGroupDto
import com.nl.kaviyy.core.network.apiCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

data class SearchSeries(
    val id: Int,
    val name: String,
    val libraryName: String?,
    val volumes: Int,
)

data class SearchChapter(
    val id: Int,
    val title: String?,
    /** Chapter number/range ("87"); blank for loose volumes. */
    val range: String,
)

/** A collection or reading list. */
data class SearchList(
    val id: Int,
    val title: String,
    val items: Int,
)

data class SearchFile(
    val id: Int,
    /** File name only (never the server path). */
    val name: String,
)

data class SearchBookmark(
    val seriesId: Int,
    val chapterId: Int,
    val seriesName: String,
)

/** `api/Search/search`, grouped as the server returns it. */
data class SearchResults(
    val series: List<SearchSeries> = emptyList(),
    val chapters: List<SearchChapter> = emptyList(),
    val people: List<NamedRef> = emptyList(),
    val genres: List<NamedRef> = emptyList(),
    val tags: List<NamedRef> = emptyList(),
    val collections: List<SearchList> = emptyList(),
    val readingLists: List<SearchList> = emptyList(),
    val files: List<SearchFile> = emptyList(),
    val bookmarks: List<SearchBookmark> = emptyList(),
)

/** Search (network only) and per-server recent queries (Room). */
interface SearchRepository {
    suspend fun search(
        server: Server,
        query: String,
    ): AppResult<SearchResults>

    /** The series a chapter belongs to (chapter hits carry no series id). */
    suspend fun seriesForChapter(
        server: Server,
        chapterId: Int,
    ): AppResult<Int>

    suspend fun seriesForFile(
        server: Server,
        fileId: Int,
    ): AppResult<Int>

    /** The filter field for a person's first role (search hits carry no role). */
    suspend fun personField(
        server: Server,
        personId: Int,
    ): AppResult<FilterField>

    fun recents(server: Server): Flow<List<String>>

    suspend fun addRecent(
        server: Server,
        query: String,
    )

    suspend fun clearRecents(server: Server)
}

private const val RECENT_LIMIT = 8

class KavitaSearchRepository
    @Inject
    constructor(
        private val apis: ServerApis,
        private val recents: RecentSearchDao,
    ) : SearchRepository {
        override suspend fun search(
            server: Server,
            query: String,
        ): AppResult<SearchResults> =
            apiCall {
                apis.api(server).search(query.trim(), includeChapterAndFiles = true)
            }.map { it.toDomain() }

        override suspend fun seriesForChapter(
            server: Server,
            chapterId: Int,
        ): AppResult<Int> = apiCall { apis.api(server).seriesForChapter(chapterId).id }

        override suspend fun seriesForFile(
            server: Server,
            fileId: Int,
        ): AppResult<Int> = apiCall { apis.api(server).seriesForFile(fileId).id }

        override suspend fun personField(
            server: Server,
            personId: Int,
        ): AppResult<FilterField> =
            apiCall { apis.api(server).personRoles(personId) }.map { roles ->
                roles.firstNotNullOfOrNull(::personField) ?: FilterField.Writers
            }

        override fun recents(server: Server): Flow<List<String>> = recents.observe(server.id.value, RECENT_LIMIT)

        override suspend fun addRecent(
            server: Server,
            query: String,
        ) {
            val q = query.trim()
            if (q.isNotEmpty()) {
                recents.add(
                    RecentSearchEntity(server.id.value, q, System.currentTimeMillis()),
                    RECENT_LIMIT,
                )
            }
        }

        override suspend fun clearRecents(server: Server) = recents.clear(server.id.value)
    }

internal fun SearchResultGroupDto.toDomain() =
    SearchResults(
        series =
            series.map {
                SearchSeries(
                    it.seriesId,
                    it.localizedName?.takeIf(String::isNotBlank) ?: it.name.orEmpty(),
                    it.libraryName,
                    it.volumeCount,
                )
            },
        chapters =
            chapters.map {
                SearchChapter(
                    it.id,
                    it.titleName?.takeIf(String::isNotBlank),
                    it.range.orEmpty(),
                )
            },
        people = persons.mapNotNull { p -> p.name?.let { NamedRef(p.id, it) } },
        genres = genres.mapNotNull { g -> g.title?.let { NamedRef(g.id, it) } },
        tags = tags.mapNotNull { t -> t.title?.let { NamedRef(t.id, it) } },
        collections = collections.map { SearchList(it.id, it.title.orEmpty(), it.itemCount) },
        readingLists = readingLists.map { SearchList(it.id, it.title.orEmpty(), it.itemCount) },
        // Path separators from any OS; only the last segment is shown.
        files =
            files.map { f ->
                SearchFile(
                    f.id,
                    f.filePath
                        .orEmpty()
                        .substringAfterLast('/')
                        .substringAfterLast('\\'),
                )
            },
        bookmarks =
            bookmarks.map {
                SearchBookmark(
                    it.seriesId,
                    it.chapterId,
                    it.localizedSeriesName?.takeIf(String::isNotBlank) ?: it.seriesName.orEmpty(),
                )
            },
    )
