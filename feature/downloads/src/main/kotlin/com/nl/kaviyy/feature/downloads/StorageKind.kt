package com.nl.kaviyy.feature.downloads

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.download.DownloadItem

/** Storage card groups, in bar order (Kavita `LibraryType` → kind). */
enum class StorageKind(
    @param:StringRes val label: Int,
    /** Accent shade in the stacked bar. */
    val alpha: Float,
) {
    Manga(R.string.downloads_kind_manga, 1f),
    Comics(R.string.downloads_kind_comics, 0.7f),
    Books(R.string.downloads_kind_books, 0.45f),
    Other(R.string.downloads_kind_other, 0.25f),
}

@Suppress("MagicNumber") // Kavita LibraryType values
internal fun storageKind(libraryType: Int?): StorageKind =
    when (libraryType) {
        0 -> StorageKind.Manga

        1, 5 -> StorageKind.Comics

        // Comic, ComicVine
        2, 4 -> StorageKind.Books

        // Book, LightNovel
        else -> StorageKind.Other
    }

/** Bytes per kind, in [StorageKind] order, leaving out empty kinds. */
internal fun List<DownloadItem>.storageBreakdown(): List<Pair<StorageKind, Long>> {
    val byKind = groupBy { storageKind(it.libraryType) }.mapValues { (_, items) -> items.sumOf { it.row.bytesDone } }
    return StorageKind.entries.mapNotNull { k -> byKind[k]?.takeIf { it > 0 }?.let { k to it } }
}

/** [derived] "2.1 GB used" / "28.4 GB free on device", a stacked 6dp bar by kind and legend chips. */
@Composable
internal fun StorageCard(
    usedBytes: Long,
    freeBytes: Long?,
    parts: List<Pair<StorageKind, Long>>,
) {
    val context = LocalContext.current
    val accent = Kavita.colors.accent
    Column(
        Modifier.fillMaxWidth().background(Kavita.colors.surface, KavitaShapes.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.downloads_used, Formatter.formatShortFileSize(context, usedBytes)),
            style = Kavita.type.title2.copy(fontWeight = FontWeight.ExtraBold),
            color = Kavita.colors.label,
        )
        freeBytes?.let {
            Text(
                stringResource(R.string.downloads_free, Formatter.formatShortFileSize(context, it)),
                style = Kavita.type.footnote,
                color = Kavita.colors.labelSecondary,
            )
        }
        Row(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(Kavita.colors.track),
        ) {
            parts.forEach { (kind, bytes) ->
                Box(Modifier.weight(bytes.toFloat()).fillMaxHeight().background(accent.copy(alpha = kind.alpha)))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            parts.forEach { (kind, bytes) ->
                Legend(accent.copy(alpha = kind.alpha), kind.label, Formatter.formatShortFileSize(context, bytes))
            }
        }
    }
}

@Composable
private fun Legend(
    color: Color,
    @StringRes label: Int,
    size: String,
) = Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
    Text("${stringResource(label)} · $size", style = Kavita.type.caption, color = Kavita.colors.labelSecondary)
}
