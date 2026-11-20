package com.nl.kaviyy.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nl.kaviyy.core.data.SearchResults

/** Result groups in canvas order; files and bookmarks only under All (no scope chip for them). */
enum class GroupKind { Series, Chapters, People, GenresTags, ReadingLists, Collections, Files, Bookmarks }

/** One row; [hit] is null for results that can't open yet (lists: M13). [key] seeds the placeholder color. */
data class ResultRow(
    val key: Int,
    val title: String,
    val hit: SearchHit?,
    val detail: Detail,
)

/** What the subtitle says. */
sealed interface Detail {
    data class Series(
        val library: String?,
        val volumes: Int,
    ) : Detail

    data class Chapter(
        val range: String,
    ) : Detail

    data object Person : Detail

    data object None : Detail

    data class Items(
        val count: Int,
        val readingList: Boolean,
    ) : Detail

    data object File : Detail

    data object Bookmark : Detail
}

data class ResultGroup(
    val kind: GroupKind,
    val rows: List<ResultRow>,
)

internal fun groups(
    r: SearchResults,
    scope: SearchScope,
): List<ResultGroup> {
    val all =
        listOf(
            ResultGroup(
                GroupKind.Series,
                r.series.map {
                    ResultRow(it.id, it.name, SearchHit.Series(it.id), Detail.Series(it.libraryName, it.volumes))
                },
            ),
            ResultGroup(
                GroupKind.Chapters,
                r.chapters.map {
                    ResultRow(
                        it.id,
                        it.title ?: it.range,
                        SearchHit.Chapter(it.id),
                        Detail.Chapter(it.range),
                    )
                },
            ),
            ResultGroup(
                GroupKind.People,
                r.people.map {
                    ResultRow(it.id, it.name, SearchHit.Person(it.id, it.name), Detail.Person)
                },
            ),
            ResultGroup(
                GroupKind.GenresTags,
                r.genres.map { ResultRow(it.id, it.name, SearchHit.Genre(it.id, it.name), Detail.None) } +
                    r.tags.map { ResultRow(it.id, it.name, SearchHit.Tag(it.id, it.name), Detail.None) },
            ),
            // TODO(M13): open reading lists and collections.
            ResultGroup(
                GroupKind.ReadingLists,
                r.readingLists.map {
                    ResultRow(it.id, it.title, null, Detail.Items(it.items, true))
                },
            ),
            ResultGroup(
                GroupKind.Collections,
                r.collections.map {
                    ResultRow(it.id, it.title, null, Detail.Items(it.items, false))
                },
            ),
            ResultGroup(GroupKind.Files, r.files.map { ResultRow(it.id, it.name, SearchHit.File(it.id), Detail.File) }),
            ResultGroup(
                GroupKind.Bookmarks,
                r.bookmarks.map {
                    ResultRow(it.seriesId, it.seriesName, SearchHit.Series(it.seriesId), Detail.Bookmark)
                },
            ),
        )
    return all.filter { it.rows.isNotEmpty() && it.kind in scope.kinds() }
}

private fun SearchScope.kinds(): Set<GroupKind> =
    when (this) {
        SearchScope.All -> GroupKind.entries.toSet()
        SearchScope.Series -> setOf(GroupKind.Series)
        SearchScope.Chapters -> setOf(GroupKind.Chapters)
        SearchScope.People -> setOf(GroupKind.People)
        SearchScope.Tags -> setOf(GroupKind.GenresTags)
        SearchScope.Lists -> setOf(GroupKind.ReadingLists, GroupKind.Collections)
    }

/** Up to two initials for a person avatar. */
internal fun initials(name: String): String =
    name
        .split(' ', '-', '.')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

internal fun SearchScope.label(): Int =
    when (this) {
        SearchScope.All -> R.string.search_scope_all
        SearchScope.Series -> R.string.search_scope_series
        SearchScope.Chapters -> R.string.search_scope_chapters
        SearchScope.People -> R.string.search_scope_people
        SearchScope.Tags -> R.string.search_scope_tags
        SearchScope.Lists -> R.string.search_scope_lists
    }

internal fun GroupKind.label(): Int =
    when (this) {
        GroupKind.Series -> R.string.search_group_series
        GroupKind.Chapters -> R.string.search_group_chapters
        GroupKind.People -> R.string.search_group_people
        GroupKind.GenresTags -> R.string.search_group_genres_tags
        GroupKind.ReadingLists -> R.string.search_group_reading_lists
        GroupKind.Collections -> R.string.search_group_collections
        GroupKind.Files -> R.string.search_group_files
        GroupKind.Bookmarks -> R.string.search_group_bookmarks
    }

@Composable
internal fun ResultRow.subtitle(): String =
    when (val d = detail) {
        is Detail.Series -> {
            val volumes = pluralStringResource(R.plurals.search_volumes, d.volumes, d.volumes)
            d.library?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.search_dot, it, volumes) } ?: volumes
        }

        is Detail.Chapter -> {
            if (d.range.isBlank()) {
                stringResource(
                    R.string.search_chapter,
                )
            } else {
                stringResource(R.string.search_chapter_number, d.range)
            }
        }

        Detail.Person -> {
            stringResource(R.string.search_person)
        }

        Detail.None -> {
            ""
        }

        is Detail.Items -> {
            val items = pluralStringResource(R.plurals.search_items, d.count, d.count)
            stringResource(
                R.string.search_dot,
                items,
                stringResource(if (d.readingList) R.string.search_reading_list else R.string.search_collection),
            )
        }

        Detail.File -> {
            stringResource(R.string.search_file)
        }

        Detail.Bookmark -> {
            stringResource(R.string.search_bookmark)
        }
    }
