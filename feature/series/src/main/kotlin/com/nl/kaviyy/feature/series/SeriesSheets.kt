package com.nl.kaviyy.feature.series

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.ReadTarget
import com.nl.kaviyy.core.data.SendTarget
import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.data.isAdmin
import com.nl.kaviyy.core.data.minutesLeft
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.designsystem.component.CoverImage
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.GroupedThumbRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.chapterCoverUrl
import com.nl.kaviyy.core.network.volumeCoverUrl
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Volume or chapter detail over the series (FEATURES M8.2). */
@Composable
internal fun SeriesDetailSheet(
    sheet: SeriesSheet,
    state: SeriesUiState,
    detail: SeriesDetail,
    drafts: SheetDrafts,
    actions: SeriesActions,
) {
    val close = { actions.onSheet(null) }
    val server = state.server
    when (sheet) {
        SeriesSheet.Reviews -> {
            SheetFrame(
                stringResource(R.string.series_reviews_title),
                close,
            ) { ReviewsContent(state, drafts.review, actions) }
        }

        is SeriesSheet.Volume -> {
            val volume = detail.volumes.firstOrNull { it.id == sheet.id } ?: return
            val chapters = detail.chapters.filter { it.volumeId == volume.id }
            SheetFrame(stringResource(R.string.series_volume, volume.label()), close) {
                Facts(
                    coverUrl = server?.volumeCoverUrl(volume.id),
                    colorKey = volume.seriesId,
                    pages = volume.pages,
                    pagesRead = volume.pagesRead,
                    bytes = chapters.sumOf { it.fileBytes },
                )
                ReadButton(nextIn(chapters), actions)
                MarkRow(
                    volume.pages > 0 && volume.pagesRead >= volume.pages,
                    ReadTarget.Volume(volume.seriesId, volume.id),
                    actions,
                    SendTarget.Chapters(chapters.map { it.id }),
                )
                if (chapters.size > 1) {
                    SectionHeader(stringResource(R.string.series_chapters_header), Modifier.padding(top = 8.dp))
                    GroupedList {
                        chapters.forEachIndexed { i, c ->
                            val state = rowState(c.pages, c.pagesRead, asPagesLeft = true)
                            GroupedThumbRow(
                                title = chapterTitle(c),
                                subtitle = pluralStringResource(R.plurals.series_pages, c.pages, c.pages),
                                state = stateText(state),
                                stateColor = stateColor(state),
                                onClick = { actions.onSheet(SeriesSheet.Chapter(c.id)) },
                                divider = i < chapters.lastIndex,
                            ) { Thumb(c.seriesId, server?.chapterCoverUrl(c.id)) }
                        }
                    }
                }
            }
        }

        SeriesSheet.EditSummary -> {
            SheetFrame(stringResource(R.string.series_edit_summary), close) {
                EditSummaryContent(drafts.summary, actions.admin.onSaveSummary)
            }
        }

        SeriesSheet.Actions -> {
            val series = detail.series
            if (series != null) {
                SheetFrame(stringResource(R.string.series_actions_title), close) {
                    GroupedList {
                        GroupedRow(
                            stringResource(R.string.series_mark_series_read),
                            onClick = { actions.onMark(ReadTarget.Series(series.id), true) },
                            showChevron = false,
                            divider = true,
                        )
                        GroupedRow(
                            stringResource(R.string.series_mark_series_unread),
                            onClick = { actions.onMark(ReadTarget.Series(series.id), false) },
                            showChevron = false,
                            divider = true,
                        )
                        GroupedRow(
                            stringResource(R.string.series_send_series_to_device),
                            onClick = { actions.onSendToDevice(SendTarget.Series(series.id)) },
                            divider = true,
                        )
                        GroupedRow(
                            stringResource(R.string.series_download_series),
                            onClick = { actions.onDownload(detail.chapters.map { it.id }) },
                            showChevron = false,
                        )
                    }
                    if (server?.isAdmin == true) AdminSection(actions.admin)
                }
            }
        }

        is SeriesSheet.Chapter -> {
            val chapter = detail.chapters.firstOrNull { it.id == sheet.id } ?: return
            SheetFrame(chapterTitle(chapter), close) {
                Facts(
                    coverUrl = server?.chapterCoverUrl(chapter.id),
                    colorKey = chapter.seriesId,
                    pages = chapter.pages,
                    pagesRead = chapter.pagesRead,
                    bytes = chapter.fileBytes,
                    released = chapter.releaseDate,
                    minutes = chapter.minutesLeft().takeIf { chapter.pagesRead < chapter.pages },
                )
                ReadButton(chapter, actions)
                MarkRow(
                    chapter.pages > 0 && chapter.pagesRead >= chapter.pages,
                    ReadTarget.Chapter(chapter.seriesId, chapter.id),
                    actions,
                    SendTarget.Chapters(listOf(chapter.id)),
                )
            }
        }
    }
}

@Composable
private fun SheetFrame(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) = KavitaBottomSheet(onDismiss = onClose) {
    SheetHeader(title, stringResource(R.string.series_done), onClose)
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun Facts(
    coverUrl: String?,
    colorKey: Int,
    pages: Int,
    pagesRead: Int,
    bytes: Long,
    released: Long = 0,
    minutes: Int? = null,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        CoverImage(
            "",
            coverColorFor(colorKey.toLong()),
            Modifier.size(96.dp, 144.dp),
            KavitaShapes.coverGrid,
            imageUrl = coverUrl,
        )
        GroupedList(Modifier.weight(1f)) {
            val rows =
                listOfNotNull(
                    stringResource(R.string.series_fact_pages) to pages.toString(),
                    stringResource(R.string.series_fact_progress) to
                        stateText(rowState(pages, pagesRead, asPagesLeft = false)),
                    minutes?.let {
                        stringResource(R.string.series_fact_time) to
                            stringResource(R.string.series_minutes, it)
                    },
                    released.takeIf { it > 0 }?.let {
                        val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        stringResource(R.string.series_fact_released) to
                            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
                    },
                    bytes.takeIf { it > 0 }?.let {
                        stringResource(R.string.series_fact_size) to
                            Formatter.formatShortFileSize(context, it)
                    },
                )
            rows.forEachIndexed { i, (label, value) -> GroupedRow(label, value = value, divider = i < rows.lastIndex) }
        }
    }
}

@Composable
private fun ReadButton(
    chapter: ChapterEntity?,
    actions: SeriesActions,
) {
    if (chapter == null) return
    PrimaryButton(stringResource(R.string.series_read), {
        actions.onRead(chapter.id)
    }, Modifier.fillMaxWidth(), leadingIcon = KavitaIcons.Play)
}

/** Flips a volume/chapter between fully read and unread. */
@Composable
private fun MarkRow(
    isRead: Boolean,
    target: ReadTarget,
    actions: SeriesActions,
    send: SendTarget,
) = GroupedList {
    GroupedRow(
        stringResource(if (isRead) R.string.series_mark_unread else R.string.series_mark_read),
        onClick = { actions.onMark(target, !isRead) },
        showChevron = false,
        divider = true,
    )
    GroupedRow(
        stringResource(R.string.series_send_to_device),
        onClick = { actions.onSendToDevice(send) },
        divider = true,
    )
    GroupedRow(
        stringResource(R.string.series_download),
        onClick = { actions.onDownload((send as? SendTarget.Chapters)?.ids.orEmpty()) },
        showChevron = false,
    )
}

/** Text drafts owned by the ViewModel, edited inside sheets. */
internal data class SheetDrafts(
    val review: TextFieldState,
    val summary: TextFieldState,
)
