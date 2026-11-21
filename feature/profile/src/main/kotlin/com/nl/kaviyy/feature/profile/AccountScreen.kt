package com.nl.kaviyy.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing

@Composable
fun AccountRoute(
    onBack: () -> Unit,
    onAuthKeys: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountScreen(
        state,
        AccountFields(viewModel.current, viewModel.new, viewModel.confirm, viewModel.email),
        AccountActions(
            onBack = onBack,
            onSheet = viewModel::showSheet,
            onSavePassword = viewModel::savePassword,
            onSaveEmail = viewModel::saveEmail,
            onAuthKeys = onAuthKeys,
            onSignOut = onSignOut,
        ),
    )
}

/** [derived] Account: who you are, password, e-mail, Auth Keys, sign out. */
@Composable
fun AccountScreen(
    state: AccountUiState,
    fields: AccountFields,
    actions: AccountActions,
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
        LargeTitleHeader(stringResource(R.string.account_title))
        GroupedList {
            GroupedRow(
                stringResource(R.string.account_username),
                value = state.account?.username ?: state.server?.username,
                divider = true,
            )
            GroupedRow(
                stringResource(R.string.account_email),
                value = state.account?.email ?: stringResource(R.string.account_email_none),
            )
        }
        GroupedList {
            GroupedRow(stringResource(R.string.account_change_password), onClick = {
                actions.onSheet(AccountSheet.Password)
            }, divider = true)
            GroupedRow(stringResource(R.string.account_change_email), onClick = {
                actions.onSheet(AccountSheet.Email)
            }, divider = true)
            GroupedRow(stringResource(R.string.account_auth_keys), onClick = actions.onAuthKeys)
        }
        state.notice?.let { Note(noticeText(it)) }
        GroupedList {
            GroupedRow(
                stringResource(R.string.you_sign_out),
                onClick = actions.onSignOut,
                showChevron = false,
                labelColor = Kavita.colors.destructiveText,
            )
        }
    }
    when (state.sheet) {
        AccountSheet.Password -> PasswordSheet(state, fields, actions)
        AccountSheet.Email -> EmailSheet(state, fields, actions)
        null -> Unit
    }
}

@Composable
private fun PasswordSheet(
    state: AccountUiState,
    fields: AccountFields,
    actions: AccountActions,
) = Sheet(R.string.account_change_password, state, actions, actions.onSavePassword) {
    GroupedList {
        GroupedInputRow(
            stringResource(R.string.account_current_password),
            fields.current,
            secure = true,
            divider = true,
        )
        GroupedInputRow(stringResource(R.string.account_new_password), fields.new, secure = true, divider = true)
        GroupedInputRow(stringResource(R.string.account_confirm_password), fields.confirm, secure = true)
    }
}

@Composable
private fun EmailSheet(
    state: AccountUiState,
    fields: AccountFields,
    actions: AccountActions,
) = Sheet(R.string.account_change_email, state, actions, actions.onSaveEmail) {
    GroupedList {
        GroupedInputRow(
            stringResource(R.string.account_new_email),
            fields.email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            divider = true,
        )
        GroupedInputRow(stringResource(R.string.account_current_password), fields.current, secure = true)
    }
    Note(R.string.account_email_hint)
}

@Composable
private fun Sheet(
    @StringRes title: Int,
    state: AccountUiState,
    actions: AccountActions,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) = KavitaBottomSheet(onDismiss = { actions.onSheet(null) }) {
    SheetHeader(
        stringResource(title),
        stringResource(R.string.devices_cancel),
        { actions.onSheet(null) },
        applyLabel = stringResource(R.string.devices_save),
        onApply = { if (!state.busy) onSave() },
    )
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        state.problem?.let { Note(problemText(it)) }
    }
}

@Composable
private fun Note(
    @StringRes text: Int,
) = Text(stringResource(text), style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)

private fun problemText(p: AccountProblem) =
    when (p) {
        AccountProblem.PasswordLength -> R.string.account_password_length
        AccountProblem.PasswordMismatch -> R.string.account_password_mismatch
        AccountProblem.InvalidEmail -> R.string.account_invalid_email
        AccountProblem.Failed -> R.string.account_failed
    }

private fun noticeText(n: AccountNotice) =
    when (n) {
        AccountNotice.PasswordChanged -> R.string.account_password_changed
        AccountNotice.EmailSent -> R.string.account_email_sent
        AccountNotice.EmailNotSent -> R.string.account_email_not_sent
    }
