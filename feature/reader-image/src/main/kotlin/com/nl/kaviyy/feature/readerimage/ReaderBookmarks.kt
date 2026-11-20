package com.nl.kaviyy.feature.readerimage

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.kaviyy.core.data.ReaderChapter
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.ImageReaderColors
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import kotlin.math.roundToInt

/** Tap: bookmark this page. Long press: this chapter's bookmarks, to jump to or remove [derived]. */
@Composable
internal fun BookmarkButton(
    state: ImageReaderUiState,
    actions: ImageReaderActions,
) = Box {
    var open by remember { mutableStateOf(false) }
    val on = state.page in state.bookmarks
    Box(
        Modifier
            .size(TOP_BUTTON)
            .clip(KavitaShapes.pill)
            .background(ImageReaderColors.chrome)
            .combinedClickable(
                role = Role.Button,
                onClickLabel =
                    stringResource(
                        if (on) R.string.reader_bookmark_remove else R.string.reader_bookmark_add,
                    ),
                onLongClickLabel = stringResource(R.string.reader_bookmarks),
                onLongClick = { open = true },
            ) { actions.onBookmark(state.page) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) KavitaIcons.BookmarkFilled else KavitaIcons.Bookmark,
            stringResource(R.string.reader_bookmark_page),
            Modifier.size(20.dp),
            tint = if (on) ImageReaderColors.accent else ImageReaderColors.content,
        )
    }
    DropdownMenu(open, { open = false }, containerColor = ImageReaderColors.chrome, shape = KavitaShapes.card) {
        if (state.bookmarks.isEmpty()) {
            Text(
                stringResource(R.string.reader_bookmarks_empty),
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = Kavita.type.footnote,
                color = ImageReaderColors.secondary,
            )
        }
        state.bookmarks.sorted().forEach { page ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.reader_page_image, page + 1),
                        color = ImageReaderColors.content,
                    )
                },
                onClick = {
                    open = false
                    actions.onPage(page)
                },
                trailingIcon = {
                    CircleIconButton(
                        KavitaIcons.Close,
                        stringResource(R.string.reader_bookmark_delete, page + 1),
                        { actions.onBookmark(page) },
                        containerColor = Color.Transparent,
                        contentColor = ImageReaderColors.secondary,
                        modifier = Modifier.size(TOP_BUTTON),
                    )
                },
            )
        }
    }
}
