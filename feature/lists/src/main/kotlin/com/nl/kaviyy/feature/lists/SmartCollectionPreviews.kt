package com.nl.kaviyy.feature.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SmartRule
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import kotlinx.coroutines.flow.flowOf

private val tags =
    listOf("Isekai", "Time Travel", "Found Family", "Slow Burn", "Magic School", "Revenge", "Cooking", "Villainess")
        .mapIndexed { i, t -> RuleValue("${i + 10}", t) }
private val genres = listOf("Fantasy", "Romance", "Comedy", "Drama").mapIndexed { i, g -> RuleValue("${i + 1}", g) }

internal val previewSmart =
    SmartCollectionUiState(
        open = true,
        tags = tags,
        genres = genres,
        selectedTags = tags.take(2),
        selectedGenres = genres.take(1),
        count = MatchCount.Count(23),
    )

/** The sheet's form on its own (a ModalBottomSheet doesn't render in previews/screenshots). */
@Composable
internal fun SmartCollectionPreview(
    appearance: Appearance = Appearance.Light,
    state: SmartCollectionUiState = previewSmart,
    actions: SmartCollectionActions = SmartCollectionActions(),
) = KavitaTheme(appearance = appearance) {
    SmartCollectionForm(
        state,
        TextFieldState("Isekai picks"),
        TextFieldState(),
        actions,
        Modifier.fillMaxSize().background(Kavita.colors.background),
    )
}

@Composable
internal fun SmartCollectionScreenPreview(appearance: Appearance = Appearance.Light) =
    KavitaTheme(appearance = appearance) {
        val done = LoadState.NotLoading(endOfPaginationReached = true)
        val items = flowOf(PagingData.from(previewWantToRead, LoadStates(done, done, done))).collectAsLazyPagingItems()
        CollectionScreen(
            CollectionUiState(
                collection =
                    previewCollections[0].copy(
                        title = "Isekai picks",
                        summary =
                            SmartRule.summaryWith(
                                "Comfy portal fantasies.",
                                SmartRule(false, tags.take(2), genres.take(1)),
                            ),
                    ),
                editable = true,
            ),
            items,
            {},
            {},
        )
    }

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun SmartLight() = SmartCollectionPreview()

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun SmartDark() = SmartCollectionPreview(Appearance.Dark)

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun SmartCollectionScreenLight() = SmartCollectionScreenPreview()
