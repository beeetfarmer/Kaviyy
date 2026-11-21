package com.nl.kaviyy.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.download.downloadState

/** [derived] DESIGN.md tablet: Series detail goes two-pane from 600dp. */
internal val TWO_PANE_MIN_WIDTH = 600.dp

private const val HERO_PANE_WEIGHT = 0.45f
private const val LISTS_PANE_WEIGHT = 0.55f

/** Hero band across the top; the hero column (left) and the lists (right) scroll on their own. */
@Composable
internal fun TwoPane(
    heroColor: Color,
    heroHeight: Dp,
    hero: @Composable () -> Unit,
    lists: @Composable () -> Unit,
) = Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxWidth().height(heroHeight).background(heroColor))
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(HERO_PANE_WEIGHT).fillMaxHeight().verticalScroll(rememberScrollState())) { hero() }
        Column(
            Modifier
                .weight(LISTS_PANE_WEIGHT)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = heroHeight + 16.dp, end = KavitaSpacing.screenHorizontal)
                .padding(bottom = KavitaSpacing.contentBottomPadding),
        ) { lists() }
    }
}

/** Volumes / Chapters / Specials / Related tabs. */
@Composable
internal fun SeriesLists(
    state: SeriesUiState,
    actions: SeriesActions,
) {
    state.detail?.let {
        SeriesTabs(
            it,
            state.server,
            state.tab,
            state.related,
            actions,
            state.downloads.associate { d -> d.chapterId to d.downloadState },
            dimmed = { c -> !state.canRead(c) },
        )
    }
}
