package com.nl.kaviyy.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ListsRepository
import com.nl.kaviyy.core.data.editableBy
import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

data class CollectionsSheetState(
    /** The user's own, editable collections. */
    val collections: List<CollectionEntity> = emptyList(),
    /** Ids holding this series; null until loaded. */
    val member: Set<Int>? = null,
    val busy: Boolean = false,
    val error: AppError? = null,
)

private data class Local(
    val member: Set<Int>? = null,
    val busy: Boolean = false,
    val error: AppError? = null,
)

/** [derived] Add to collection: toggle the series in each own collection, or start a new one with it. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionsSheetViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val lists: ListsRepository,
    ) : ViewModel() {
        val newName = TextFieldState()
        private val local = MutableStateFlow(Local())
        private var seriesId = 0

        val state: StateFlow<CollectionsSheetState> =
            session.activeServer
                .flatMapLatest { s ->
                    if (s ==
                        null
                    ) {
                        flowOf(emptyList())
                    } else {
                        lists.collections(s).map { all -> all.filter { it.editableBy(s) } }
                    }
                }.combine(local) { mine, l -> CollectionsSheetState(mine, l.member, l.busy, l.error) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CollectionsSheetState())

        fun open(series: Int) {
            seriesId = series
            run { s ->
                lists.refreshCollections(s)
                lists.collectionsWith(s, series).also { r ->
                    r.getOrNull()?.let { m -> local.update { it.copy(member = m) } }
                }
            }
        }

        fun toggle(collection: CollectionEntity) {
            val member = local.value.member ?: return
            val inIt = collection.id !in member
            local.update { it.copy(member = if (inIt) member + collection.id else member - collection.id) }
            run { s ->
                lists.setInCollection(s, collection, seriesId, inIt).also { r ->
                    if (r is AppResult.Failure) local.update { it.copy(member = member) } // undo
                }
            }
        }

        fun create() {
            val name = newName.text.toString().trim()
            if (name.isEmpty()) return
            run { s ->
                lists.createCollection(s, name, seriesId).also { r ->
                    if (r is AppResult.Success) {
                        newName.clearText()
                        lists.collectionsWith(s, seriesId).getOrNull()?.let { m ->
                            local.update { it.copy(member = m) }
                        }
                    }
                }
            }
        }

        private fun run(block: suspend (Server) -> AppResult<*>) {
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                local.update { it.copy(busy = true, error = null) }
                val r = block(s)
                local.update { it.copy(busy = false, error = (r as? AppResult.Failure)?.error) }
            }
        }
    }

@Composable
internal fun CollectionsSheet(
    seriesId: Int,
    onDismiss: () -> Unit,
    viewModel: CollectionsSheetViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { viewModel.open(seriesId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectionsSheetContent(state, viewModel.newName, onDismiss, viewModel::toggle, viewModel::create)
}

@Composable
internal fun CollectionsSheetContent(
    state: CollectionsSheetState,
    newName: TextFieldState,
    onDismiss: () -> Unit,
    onToggle: (CollectionEntity) -> Unit,
    onCreate: () -> Unit,
) = KavitaBottomSheet(onDismiss = onDismiss) {
    SheetHeader(stringResource(R.string.series_collections_title), stringResource(R.string.series_done), onDismiss)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        if (state.collections.isNotEmpty()) {
            SectionHeader(stringResource(R.string.series_collections_yours))
            GroupedList {
                state.collections.forEachIndexed { i, c ->
                    val on = state.member?.contains(c.id) == true
                    GroupedRow(
                        c.title,
                        onClick = { onToggle(c) }.takeIf { state.member != null },
                        showChevron = false,
                        divider = i < state.collections.lastIndex,
                    ) {
                        if (on) {
                            Icon(
                                KavitaIcons.Check,
                                stringResource(R.string.series_collections_in),
                                Modifier.size(20.dp),
                                tint = Kavita.colors.accent,
                            )
                        }
                    }
                }
            }
        }
        SectionHeader(stringResource(R.string.series_collections_new), Modifier.padding(top = 12.dp))
        GroupedList {
            GroupedInputRow(
                stringResource(R.string.series_collections_name),
                newName,
                placeholder = stringResource(R.string.series_collections_name_hint),
            )
        }
        state.error?.let {
            Text(
                stringResource(R.string.series_collections_failed),
                Modifier.padding(top = 8.dp),
                style = Kavita.type.footnote,
                color = Kavita.colors.labelSecondary,
            )
        }
        PrimaryButton(
            stringResource(R.string.series_collections_create),
            onCreate,
            Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = !state.busy && newName.text.isNotBlank(),
        )
    }
}

/** 50dp round action, like the Want to Read toggle [derived]. */
@Composable
internal fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) = Box(
    Modifier
        .size(50.dp)
        .clip(CircleShape)
        .background(Kavita.colors.surface)
        .border(0.5.dp, Kavita.colors.hairline, CircleShape)
        .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Icon(icon, label, Modifier.size(20.dp), tint = Kavita.colors.accent)
}
