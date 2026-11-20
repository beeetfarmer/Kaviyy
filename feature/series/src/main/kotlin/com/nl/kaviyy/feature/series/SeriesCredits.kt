package com.nl.kaviyy.feature.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.SeriesMetadata
import com.nl.kaviyy.core.data.personField
import com.nl.kaviyy.core.designsystem.component.FilterChip
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.SectionHeader

/** [derived] Genres & tags as chips and people as rows (canvas has no credits block); each opens Browse. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Credits(
    meta: SeriesMetadata,
    onBrowse: (BrowseTarget) -> Unit,
) {
    val chips =
        meta.genres.map { BrowseTarget(FilterField.Genres, it.id, it.name) } +
            meta.tags.map { BrowseTarget(FilterField.Tags, it.id, it.name) }
    val people = meta.people.mapNotNull { p -> personField(p.role)?.let { BrowseTarget(it, p.id, p.name) } }
    if (chips.isEmpty() && people.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (chips.isNotEmpty()) {
            SectionHeader(stringResource(R.string.series_genres_tags))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chips.forEach { FilterChip(it.name, selected = false, onClick = { onBrowse(it) }) }
            }
        }
        if (people.isNotEmpty()) {
            SectionHeader(stringResource(R.string.series_people))
            GroupedList {
                people.forEachIndexed { i, p ->
                    GroupedRow(
                        p.name,
                        value = stringResource(fieldLabel(p.field)),
                        onClick = { onBrowse(p) },
                        divider =
                            i < people.lastIndex,
                    )
                }
            }
        }
    }
}

/** Role / kind shown under a Browse title and next to a person. */
internal fun fieldLabel(field: FilterField): Int =
    when (field) {
        FilterField.Genres -> R.string.browse_genre
        FilterField.Tags -> R.string.browse_tag
        FilterField.Writers -> R.string.role_writer
        FilterField.Penciller -> R.string.role_penciller
        FilterField.Inker -> R.string.role_inker
        FilterField.Colorist -> R.string.role_colorist
        FilterField.Letterer -> R.string.role_letterer
        FilterField.CoverArtist -> R.string.role_cover_artist
        FilterField.Editor -> R.string.role_editor
        FilterField.Publisher -> R.string.role_publisher
        FilterField.Characters -> R.string.role_character
        FilterField.Translators -> R.string.role_translator
        FilterField.Imprint -> R.string.role_imprint
        FilterField.Team -> R.string.role_team
        FilterField.Location -> R.string.role_location
        else -> R.string.browse_genre
    }
