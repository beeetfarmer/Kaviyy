package com.nl.kaviyy.feature.profile

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.data.AuthKey
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AuthKeysRoute(
    onBack: () -> Unit,
    viewModel: AuthKeysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AuthKeysScreen(
        state,
        viewModel.name,
        AuthKeysActions(
            onBack = onBack,
            onNew = viewModel::openNew,
            onExpiry = viewModel::setExpiry,
            onCreate = viewModel::create,
            onSelect = viewModel::select,
            onRotate = viewModel::rotate,
            onDelete = viewModel::delete,
            onClose = viewModel::close,
            onCopy = { copySecret(context, it) },
        ),
    )
}

/** Copies a key, flagged sensitive so Android 13+ hides it from the clipboard preview. */
private fun copySecret(
    context: Context,
    secret: String,
) {
    val clip = ClipData.newPlainText("Auth Key", secret)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/** [derived] Auth Keys: name with "…last4 · expires", a new-key row; system keys are read-only. */
@Composable
fun AuthKeysScreen(
    state: AuthKeysUiState,
    name: TextFieldState,
    actions: AuthKeysActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Kavita.colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KavitaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapProfile),
    ) {
        CircleIconButton(
            KavitaIcons.ChevronLeft,
            stringResource(R.string.you_back),
            actions.onBack,
            Modifier.padding(top = 8.dp),
        )
        LargeTitleHeader(stringResource(R.string.account_auth_keys))
        Note(R.string.keys_intro)
        val keys = state.keys
        when {
            keys != null -> {
                if (keys.isNotEmpty()) {
                    GroupedList {
                        keys.forEachIndexed { i, k ->
                            GroupedRow(
                                k.name,
                                value = keyValue(k),
                                onClick = { actions.onSelect(k) }.takeIf { !k.system },
                                showChevron = !k.system,
                                divider = i < keys.lastIndex,
                            )
                        }
                    }
                }
                GroupedList {
                    GroupedRow(
                        stringResource(R.string.keys_new),
                        onClick = actions.onNew,
                        showChevron = false,
                        labelColor = Kavita.colors.accent,
                    )
                }
            }

            state.failed -> {
                Note(R.string.keys_offline)
            }

            else -> {
                Note(R.string.you_loading)
            }
        }
    }
    when {
        state.revealed != null -> RevealSheet(state.revealed.secret, actions)
        state.creating -> CreateSheet(state, name, actions)
        state.selected != null -> KeySheet(state.selected, state, actions)
    }
}

@Composable
private fun keyValue(k: AuthKey): String {
    val parts = mutableListOf("…" + k.hint)
    if (k.system) parts += stringResource(R.string.keys_system)
    if (k.expiresAt > 0) parts += stringResource(R.string.keys_expires, date(k.expiresAt))
    return parts.joinToString(" · ")
}

@Composable
private fun date(millis: Long): String =
    DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(LocalConfiguration.current.locales[0])
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
private fun CreateSheet(
    state: AuthKeysUiState,
    name: TextFieldState,
    actions: AuthKeysActions,
) = Sheet(R.string.keys_new, actions, R.string.keys_create, actions.onCreate.takeIf { !state.busy }) {
    GroupedList {
        GroupedInputRow(
            stringResource(R.string.devices_name),
            name,
            placeholder = stringResource(R.string.keys_name_hint),
        )
    }
    Note(R.string.keys_expiry)
    SegmentedControl(
        listOf(
            stringResource(R.string.keys_never),
            stringResource(R.string.keys_30_days),
            stringResource(R.string.keys_1_year),
        ),
        state.expiry,
        actions.onExpiry,
    )
    if (state.changeFailed) Note(R.string.devices_change_failed)
}

@Composable
private fun KeySheet(
    key: AuthKey,
    state: AuthKeysUiState,
    actions: AuthKeysActions,
) = Sheet(null, actions, null, null, title = key.name) {
    GroupedList {
        GroupedRow(stringResource(R.string.keys_created), value = date(key.createdAt), divider = true)
        GroupedRow(
            stringResource(R.string.keys_last_used),
            value = if (key.lastUsedAt > 0) date(key.lastUsedAt) else stringResource(R.string.keys_never_used),
        )
    }
    Note(R.string.keys_warning)
    GroupedList {
        GroupedRow(
            stringResource(
                if (state.confirm ==
                    KeyConfirm.Rotate
                ) {
                    R.string.keys_rotate_confirm
                } else {
                    R.string.keys_rotate
                },
            ),
            onClick = actions.onRotate.takeIf { !state.busy },
            showChevron = false,
            divider = true,
        )
        GroupedRow(
            stringResource(
                if (state.confirm ==
                    KeyConfirm.Delete
                ) {
                    R.string.keys_delete_confirm
                } else {
                    R.string.keys_delete
                },
            ),
            onClick = actions.onDelete.takeIf { !state.busy },
            showChevron = false,
            labelColor = Kavita.colors.destructive,
        )
    }
    if (state.changeFailed) Note(R.string.devices_change_failed)
}

@Composable
private fun RevealSheet(
    secret: String,
    actions: AuthKeysActions,
) = Sheet(R.string.keys_reveal_title, actions, null, null) {
    Text(
        secret,
        Modifier
            .fillMaxWidth()
            .clip(KavitaShapes.card)
            .background(Kavita.colors.surface)
            .padding(16.dp),
        style = Kavita.type.body.copy(fontFamily = FontFamily.Monospace),
        color = Kavita.colors.label,
    )
    Note(R.string.keys_reveal_note)
    PrimaryButton(stringResource(R.string.keys_copy), { actions.onCopy(secret) }, Modifier.fillMaxWidth())
}

@Composable
private fun Sheet(
    @StringRes heading: Int?,
    actions: AuthKeysActions,
    @StringRes applyLabel: Int?,
    onApply: (() -> Unit)?,
    title: String = heading?.let { stringResource(it) }.orEmpty(),
    content: @Composable () -> Unit,
) = KavitaBottomSheet(onDismiss = actions.onClose) {
    SheetHeader(
        title,
        stringResource(if (applyLabel != null) R.string.devices_cancel else R.string.keys_done),
        actions.onClose,
        applyLabel = applyLabel?.let { stringResource(it) },
        onApply = onApply ?: {},
    )
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

@Composable
private fun Note(
    @StringRes text: Int,
) = Text(stringResource(text), style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)
