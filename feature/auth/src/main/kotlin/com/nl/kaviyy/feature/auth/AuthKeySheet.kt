package com.nl.kaviyy.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.designsystem.component.ButtonSize
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedSwitchRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.component.cssShadow
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlError
import com.nl.kaviyy.core.model.server.ServerUrlResult

/** Paste-only Auth Key sign-in (QR scanning deferred). */
@Composable
internal fun AuthKeySheet(
    state: ConnectUiState,
    key: TextFieldState,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    KavitaBottomSheet(onDismiss = onDismiss) {
        AuthKeySheetContent(state, key, onSubmit, onDismiss)
    }
}

@Composable
internal fun AuthKeySheetContent(
    state: ConnectUiState,
    key: TextFieldState,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    SheetHeader(
        title = stringResource(R.string.auth_key_title),
        cancelLabel = stringResource(R.string.auth_cancel),
        onCancel = onDismiss,
        applyLabel = stringResource(if (state.signingIn) R.string.auth_signing_in else R.string.auth_sign_in),
        onApply = onSubmit,
    )
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.auth_key_help),
            style = Kavita.type.footnote,
            color = Kavita.colors.labelSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        GroupedList {
            GroupedInputRow(
                label = stringResource(R.string.auth_key_label),
                state = key,
                placeholder = stringResource(R.string.auth_key_placeholder),
                secure = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                onKeyboardAction = { onSubmit() },
            )
        }
        state.signInError?.let { SignInError(it) }
    }
}

@Composable
private fun SignInError(error: AppError) {
    Text(
        stringResource(if (error == AppError.Unauthorized) R.string.auth_error_wrong_key else error.message()),
        style = Kavita.type.footnote,
        color = Kavita.colors.destructive,
        modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Assertive },
    )
}
