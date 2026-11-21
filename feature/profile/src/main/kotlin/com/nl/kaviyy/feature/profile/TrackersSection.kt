package com.nl.kaviyy.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.Tracker
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.theme.Kavita

@StringRes
internal fun trackerName(tracker: Tracker) =
    when (tracker) {
        Tracker.AniList -> R.string.scrobbling_anilist
        Tracker.Mal -> R.string.scrobbling_mal
        Tracker.Kitsu -> R.string.trackers_kitsu
    }

/** [derived] Same grouped rows as the Kavita+ accounts, but tappable: these connect from the phone. */
@Composable
internal fun TrackersSection(
    state: ScrobblingUiState,
    actions: TrackerActions,
) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionHeader(stringResource(R.string.trackers_title))
    GroupedList {
        Tracker.entries.forEachIndexed { i, t ->
            val name = state.trackers[t]
            GroupedRow(
                stringResource(trackerName(t)),
                value =
                    name?.ifBlank { stringResource(R.string.scrobbling_connected) }
                        ?: stringResource(R.string.scrobbling_not_connected),
                onClick = { actions.onEdit(t) },
                divider = i < Tracker.entries.lastIndex,
            )
        }
    }
    Note(stringResource(R.string.trackers_hint))
}

@Composable
internal fun TrackerSheet(
    tracker: Tracker,
    state: ScrobblingUiState,
    actions: TrackerActions,
) = KavitaBottomSheet(onDismiss = { actions.onEdit(null) }) {
    val first = rememberTextFieldState()
    val second = rememberTextFieldState()
    val connected = tracker in state.trackers
    SheetHeader(
        stringResource(trackerName(tracker)),
        stringResource(R.string.devices_cancel),
        { actions.onEdit(null) },
        applyLabel = if (connected) null else stringResource(R.string.trackers_connect),
        onApply = {
            if (first.text.isNotBlank() && second.text.isNotBlank()) {
                actions.onConnect(tracker, first.text.trim().toString(), second.text.trim().toString())
            }
        },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (connected) {
            Note(stringResource(R.string.trackers_signed_in, state.trackers[tracker].orEmpty()))
            SecondaryButton(stringResource(R.string.trackers_disconnect), {
                actions.onDisconnect(tracker)
            }, Modifier.fillMaxWidth(), destructive = true)
        } else {
            TrackerForm(tracker, first, second, actions)
            when {
                state.busy -> Note(stringResource(R.string.trackers_connecting))
                state.connectFailed -> Note(stringResource(R.string.trackers_failed), error = true)
            }
        }
    }
}

@Composable
private fun TrackerForm(
    tracker: Tracker,
    first: TextFieldState,
    second: TextFieldState,
    actions: TrackerActions,
) {
    val uri = LocalUriHandler.current
    val help =
        when (tracker) {
            Tracker.AniList -> R.string.trackers_anilist_help
            Tracker.Mal -> R.string.trackers_mal_help
            Tracker.Kitsu -> R.string.trackers_kitsu_help
        }
    Note(stringResource(help))
    if (tracker == Tracker.Kitsu) {
        GroupedList {
            GroupedInputRow(
                stringResource(R.string.trackers_email),
                first,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                divider = true,
            )
            GroupedInputRow(stringResource(R.string.trackers_password), second, secure = true)
        }
        return
    }
    GroupedList { GroupedInputRow(stringResource(R.string.trackers_client_id), first) }
    SecondaryButton(
        stringResource(R.string.trackers_open, stringResource(trackerName(tracker))),
        { uri.openUri(actions.authorizeUrl(tracker, first.text.trim().toString())) },
        Modifier.fillMaxWidth(),
        enabled = first.text.isNotBlank(),
    )
    GroupedList {
        GroupedInputRow(
            stringResource(if (tracker == Tracker.AniList) R.string.trackers_token else R.string.trackers_code),
            second,
            secure = true,
        )
    }
}

@Composable
private fun Note(
    text: String,
    error: Boolean = false,
) = Text(
    text,
    style = Kavita.type.footnote,
    color = if (error) Kavita.colors.destructiveText else Kavita.colors.labelSecondary,
)
