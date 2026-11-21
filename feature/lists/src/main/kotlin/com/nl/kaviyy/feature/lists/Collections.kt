package com.nl.kaviyy.feature.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.CoverImage
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.collectionCoverUrl

/** Lists shows this many collections; "See All" opens the rest. */
internal const val COLLECTIONS_PREVIEW = 4
private const val MOSAIC = 4
private val MOSAIC_SHAPE = RoundedCornerShape(10.dp)

internal val CollectionEntity.smart: Boolean get() = source != 0

/**
 * Collection card: 2×2 colour mosaic (h 64 each, gap 4, r10) with Kavita's collection cover over it when there is
 * one, a "Smart" badge for synced collections, title 15/600, "12 series" 12.
 */
@Composable
internal fun CollectionCard(
    collection: CollectionEntity,
    server: Server?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .clip(KavitaShapes.card)
        .background(Kavita.colors.surface)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    Box(Modifier.fillMaxWidth().clip(MOSAIC_SHAPE)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0 until MOSAIC step 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in row until row + 2) {
                        Box(
                            Modifier.weight(1f).height(64.dp).background(
                                coverColorFor(collection.id * MOSAIC + i.toLong()),
                            ),
                        )
                    }
                }
            }
        }
        CoverImage(
            "",
            Color.Transparent,
            Modifier.matchParentSize(),
            shape = MOSAIC_SHAPE,
            titleSize = 0.sp,
            imageUrl = server?.collectionCoverUrl(collection.id),
        )
        if (collection.smart) SmartBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            collection.title,
            style = Kavita.type.callout.copy(fontWeight = FontWeight.SemiBold),
            color = Kavita.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            pluralStringResource(R.plurals.lists_series, collection.itemCount, collection.itemCount),
            style = Kavita.type.caption,
            color = Kavita.colors.labelSecondary,
        )
    }
}

@Composable
internal fun SmartBadge(modifier: Modifier = Modifier) =
    Text(
        stringResource(R.string.lists_smart),
        modifier
            .clip(KavitaShapes.pill)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = Kavita.type.caption.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
    )

/** Lists section: "Collections" + "See All" (when there are more), 2-column cards. */
@Composable
internal fun CollectionsSection(
    state: ListsUiState,
    actions: ListsActions,
) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            stringResource(R.string.lists_collections),
            Modifier.weight(1f),
            style = Kavita.type.title2,
            color = Kavita.colors.label,
        )
        Text(
            stringResource(R.string.lists_new_smart_short),
            Modifier
                .clickable(role = Role.Button, onClick = actions.onNewSmartCollection)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            style = Kavita.type.callout.copy(fontWeight = FontWeight.Medium),
            color = Kavita.colors.accent,
        )
        if (state.collections.size > COLLECTIONS_PREVIEW) {
            Text(
                stringResource(R.string.lists_see_all),
                Modifier.clickable(role = Role.Button, onClick = actions.onSeeAllCollections).padding(vertical = 12.dp),
                style = Kavita.type.callout.copy(fontWeight = FontWeight.Medium),
                color = Kavita.colors.accent,
            )
        }
    }
    if (state.collections.isEmpty()) {
        Text(
            stringResource(R.string.lists_no_collections),
            style = Kavita.type.callout,
            color = Kavita.colors.labelSecondary,
        )
    }
    state.collections.take(COLLECTIONS_PREVIEW).chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { c ->
                CollectionCard(c, state.server, { actions.onOpenCollection(c.id) }, Modifier.weight(1f))
            }
            if (pair.size == 1) Box(Modifier.weight(1f))
        }
    }
}

/** [derived] All collections: back, "Collections" large title, the same cards in 2 columns. */
@Composable
fun AllCollectionsScreen(
    state: ListsUiState,
    actions: ListsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = LazyVerticalGrid(
    GridCells.Fixed(2),
    modifier.fillMaxSize().background(Kavita.colors.background).statusBarsPadding(),
    contentPadding =
        PaddingValues(
            start = KavitaSpacing.screenHorizontal,
            end = KavitaSpacing.screenHorizontal,
            top = 8.dp,
            bottom = KavitaSpacing.contentBottomPadding,
        ),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.lists_back), onBack)
            LargeTitleHeader(stringResource(R.string.lists_collections))
        }
    }
    items(
        state.collections,
        key = { it.id },
    ) { c -> CollectionCard(c, state.server, { actions.onOpenCollection(c.id) }) }
}
