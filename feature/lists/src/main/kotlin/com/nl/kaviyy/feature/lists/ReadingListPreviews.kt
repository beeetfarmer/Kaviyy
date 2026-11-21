package com.nl.kaviyy.feature.lists

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.database.ReadingListEntity
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme

internal val previewReadingLists =
    listOf(
        ReadingListRow(ReadingListEntity(1, 1, "Civil War (reading order)", itemCount = 24), 14),
        ReadingListRow(ReadingListEntity(1, 2, "Batman: Year One to Knightfall", itemCount = 41), 3),
        ReadingListRow(ReadingListEntity(1, 3, "Cosmic arcs", itemCount = 9), 9),
    )

internal val previewReadingList =
    ReadingListUiState(
        list = previewReadingLists[0],
        items =
            listOf(
                ReadingListItemEntity(1, 1, 1, 0, 100, 0, 1, "Issue #1", 32, 32, "Civil War"),
                ReadingListItemEntity(1, 2, 1, 1, 101, 0, 2, "Issue #538", 24, 24, "Amazing Spider-Man"),
                ReadingListItemEntity(1, 3, 1, 2, 102, 0, 1, "Issue #2", 10, 30, "Civil War"),
                ReadingListItemEntity(1, 4, 1, 3, 103, 0, 3, "Issue #22", 0, 22, "Iron Man"),
            ),
    )

@Composable
internal fun ReadingListPreview(appearance: Appearance = Appearance.Light) =
    KavitaTheme(appearance = appearance) { ReadingListScreen(previewReadingList, ReadingListActions()) }

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ReadingListLightPreview() = ReadingListPreview()

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ReadingListDarkPreview() = ReadingListPreview(Appearance.Dark)
