package com.nl.kaviyy.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.nl.kaviyy.core.data.HomeSection
import com.nl.kaviyy.core.data.HomeSectionPref
import com.nl.kaviyy.core.data.SectionOrder
import com.nl.kaviyy.core.data.orderOf
import com.nl.kaviyy.core.data.orders
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

/** [derived] "Order": one row per section that's on and can be ordered; tapping one lists its orders. */
@Composable
internal fun OrderGroup(
    state: HomeLayoutUiState,
    actions: HomeLayoutActions,
) {
    val rows = state.layout.filter { it.visible && it.section.orders.isNotEmpty() }
    if (rows.isEmpty()) return
    Column {
        SectionHeader(stringResource(R.string.home_layout_order))
        GroupedList {
            rows.forEachIndexed { i, pref -> OrderRow(pref, state, divider = i < rows.lastIndex, actions) }
        }
        LayoutFootnoteText(stringResource(R.string.home_layout_order_footnote))
    }
}

@Composable
private fun OrderRow(
    pref: HomeSectionPref,
    state: HomeLayoutUiState,
    divider: Boolean,
    actions: HomeLayoutActions,
) = Box {
    var open by remember { mutableStateOf(false) }
    val current = state.orders.orderOf(pref)
    GroupedRow(
        rowLabel(pref, state),
        value = stringResource(orderName(current, pref.section)),
        onClick = { open = true },
        divider = divider,
    )
    DropdownMenu(open, { open = false }, containerColor = Kavita.colors.surface, shape = KavitaShapes.card) {
        pref.section.orders.forEach { order ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(orderName(order, pref.section)),
                        style = if (order == current) Kavita.type.bodyStrong else Kavita.type.body,
                        color = if (order == current) Kavita.colors.accent else Kavita.colors.label,
                    )
                },
                onClick = {
                    open = false
                    actions.onOrder(pref, order)
                },
            )
        }
    }
}

/** What "Default" means for each section, so the menu says it plainly. */
@StringRes
internal fun orderName(
    order: SectionOrder,
    section: HomeSection,
) = when (order) {
    SectionOrder.NameAsc -> {
        R.string.home_sort_az
    }

    SectionOrder.NameDesc -> {
        R.string.home_sort_za
    }

    SectionOrder.Largest -> {
        R.string.home_sort_largest
    }

    SectionOrder.Random -> {
        R.string.home_sort_random
    }

    SectionOrder.Default -> {
        when (section) {
            HomeSection.OnDeck -> R.string.home_sort_last_read
            HomeSection.SmartFilters -> R.string.home_sort_as_saved
            HomeSection.WantToRead -> R.string.home_sort_default
            HomeSection.ReadingLists, HomeSection.Downloaded -> R.string.home_sort_recent
            else -> R.string.home_sort_newest
        }
    }
}
