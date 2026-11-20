package com.nl.kaviyy.feature.series

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.Relation
import com.nl.kaviyy.core.data.displayTitle
import com.nl.kaviyy.core.data.number
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.VolumeEntity
import com.nl.kaviyy.core.designsystem.component.CoverImage
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

/** 40×60 cover thumb; placeholder colour from the series so rows of one series match. */
@Composable
internal fun Thumb(
    colorKey: Int,
    imageUrl: String?,
) = CoverImage(
    title = "", // the row's title already names it; long titles break mid-word in 40dp
    color = coverColorFor(colorKey.toLong()),
    imageUrl = imageUrl,
    shape = KavitaShapes.rowThumb,
    modifier = Modifier.size(40.dp, 60.dp),
)

@Composable
internal fun Hint(text: String) =
    Text(text, style = Kavita.type.callout, color = Kavita.colors.labelSecondary, modifier = Modifier.padding(8.dp))

internal fun VolumeEntity.label(): String = name.ifBlank { minNumber.toInt().toString() }

@Composable
internal fun chapterTitle(c: ChapterEntity): String {
    val number = c.number()
    return when {
        c.isSpecial -> c.displayTitle() ?: c.range ?: stringResource(R.string.series_special)
        number != null -> stringResource(R.string.series_chapter, number)
        else -> c.displayTitle() ?: c.range.orEmpty()
    }
}

@Composable
internal fun chapterSubtitle(c: ChapterEntity): String {
    val pages = pluralStringResource(R.plurals.series_pages, c.pages, c.pages)
    val name = c.displayTitle().takeIf { !c.isSpecial && c.number() != null }
    return listOfNotNull(name, pages).joinToString(" · ")
}

@Composable
internal fun volumeSubtitle(
    v: VolumeEntity,
    chapters: List<ChapterEntity>,
): String {
    val span = chapterSpan(chapters)?.let { stringResource(R.string.series_chapter_span, it) }
    return listOfNotNull(span, pluralStringResource(R.plurals.series_pages, v.pages, v.pages)).joinToString(" · ")
}

@Composable
internal fun stateText(state: RowState): String =
    when (state) {
        RowState.Read -> stringResource(R.string.series_state_read)
        RowState.Unread -> stringResource(R.string.series_state_unread)
        is RowState.Percent -> stringResource(R.string.series_state_percent, state.value)
        is RowState.PagesLeft -> stringResource(R.string.series_state_left, state.value)
    }

@Composable
internal fun stateColor(state: RowState): Color =
    if (state is RowState.Read || state is RowState.Unread) Kavita.colors.labelSecondary else Kavita.colors.accent

internal fun SeriesTab.label(): Int =
    when (this) {
        SeriesTab.Volumes -> R.string.series_tab_volumes
        SeriesTab.Chapters -> R.string.series_tab_chapters
        SeriesTab.Specials -> R.string.series_tab_specials
        SeriesTab.Related -> R.string.series_tab_related
    }

internal fun Relation.label(): Int =
    when (this) {
        Relation.Parent -> R.string.series_relation_parent
        Relation.Prequel -> R.string.series_relation_prequel
        Relation.Sequel -> R.string.series_relation_sequel
        Relation.SpinOff -> R.string.series_relation_spin_off
        Relation.SideStory -> R.string.series_relation_side_story
        Relation.Adaptation -> R.string.series_relation_adaptation
        Relation.AlternativeSetting -> R.string.series_relation_alternative_setting
        Relation.AlternativeVersion -> R.string.series_relation_alternative_version
        Relation.Edition -> R.string.series_relation_edition
        Relation.Annual -> R.string.series_relation_annual
        Relation.Contains -> R.string.series_relation_contains
        Relation.Character -> R.string.series_relation_character
        Relation.Doujinshi -> R.string.series_relation_doujinshi
        Relation.Other -> R.string.series_relation_other
    }
