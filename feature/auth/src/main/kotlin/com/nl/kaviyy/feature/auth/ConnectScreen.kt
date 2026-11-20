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
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.SectionHeader
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

private val AddressKeyboard =
    KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false)

@Composable
fun ConnectRoute(
    onSignIn: () -> Unit,
    onSso: () -> Unit,
    onAuthKey: () -> Unit,
    modifier: Modifier = Modifier,
    onLogoLongPress: (() -> Unit)? = null,
    viewModel: ConnectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConnectScreen(
        state = state,
        address = viewModel.address,
        username = viewModel.username,
        password = viewModel.password,
        onStaySignedIn = viewModel::setStaySignedIn,
        onSignIn = onSignIn,
        onSso = onSso,
        onAuthKey = onAuthKey,
        modifier = modifier,
        onLogoLongPress = onLogoLongPress,
    )
}

/** Screen 01 (DESIGN.md §7). */
@Composable
fun ConnectScreen(
    state: ConnectUiState,
    address: TextFieldState,
    username: TextFieldState,
    password: TextFieldState,
    onStaySignedIn: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onSso: () -> Unit,
    onAuthKey: () -> Unit,
    modifier: Modifier = Modifier,
    onLogoLongPress: (() -> Unit)? = null,
) {
    val colors = Kavita.colors
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 72.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Brand(onLogoLongPress)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.auth_section_server))
            GroupedList {
                GroupedInputRow(
                    label = stringResource(R.string.auth_address),
                    state = address,
                    placeholder = stringResource(R.string.auth_address_placeholder),
                    keyboardOptions = AddressKeyboard,
                    trailing = { ReachabilityIcon(state.reachability) },
                )
            }
            ReachabilityText(state.reachability)
            Spacer(Modifier.height(6.dp))
            SectionHeader(stringResource(R.string.auth_section_account))
            GroupedList {
                GroupedInputRow(
                    label = stringResource(R.string.auth_username),
                    state = username,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    divider = true,
                )
                GroupedInputRow(
                    label = stringResource(R.string.auth_password),
                    state = password,
                    placeholder = stringResource(R.string.auth_required),
                    secure = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )
            }
            Spacer(Modifier.height(6.dp))
            GroupedList {
                GroupedSwitchRow(stringResource(R.string.auth_stay_signed_in), state.staySignedIn, onStaySignedIn)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                stringResource(R.string.auth_sign_in),
                onSignIn,
                Modifier.fillMaxWidth(),
                size = ButtonSize.Large,
                enabled = state.canSignIn,
            )
            SecondaryButton(
                stringResource(R.string.auth_continue_sso),
                onSso,
                Modifier.fillMaxWidth(),
                size = ButtonSize.Large,
                leadingIcon = KavitaIcons.Lock,
            )
            SecondaryButton(
                stringResource(R.string.auth_use_auth_key),
                onAuthKey,
                Modifier.fillMaxWidth(),
                size = ButtonSize.Medium,
            )
            Text(
                stringResource(R.string.auth_footnote),
                style = Kavita.type.footnote,
                color = colors.labelSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Brand(onLogoLongPress: (() -> Unit)?) {
    val colors = Kavita.colors
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(84.dp)
                .cssShadow(KavitaShapes.appIcon, 12.dp, 28.dp, 0.35f, colors.accent)
                .background(colors.accent, KavitaShapes.appIcon)
                .then(
                    if (onLogoLongPress ==
                        null
                    ) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(onClick = {}, onLongClick = onLogoLongPress)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KavitaIcons.Book, null, Modifier.size(44.dp), tint = colors.onAccent)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.auth_brand),
                style = Kavita.type.largeTitle,
                color = colors.label,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.auth_subtitle),
                style = Kavita.type.bodyRegular,
                color = colors.labelSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReachabilityIcon(reachability: Reachability) {
    when (reachability) {
        Reachability.Checking -> {
            CircularProgressIndicator(Modifier.size(18.dp), color = Kavita.colors.labelSecondary, strokeWidth = 2.dp)
        }

        is Reachability.Found -> {
            Icon(
                KavitaIcons.CheckCircle,
                stringResource(R.string.auth_reachable),
                Modifier.size(20.dp),
                tint = if (reachability.supported) Kavita.colors.online else Kavita.colors.rating,
            )
        }

        else -> {
            Unit
        }
    }
}

@Composable
private fun ReachabilityText(reachability: Reachability) {
    val colors = Kavita.colors
    val (text, isError) =
        when (reachability) {
            Reachability.Idle -> {
                null to false
            }

            Reachability.Checking -> {
                stringResource(R.string.auth_checking) to false
            }

            Reachability.InsecureHttp -> {
                stringResource(R.string.auth_insecure_http) to false
            }

            is Reachability.Invalid -> {
                stringResource(reachability.error.message()) to true
            }

            is Reachability.Failed -> {
                stringResource(reachability.error.message()) to true
            }

            is Reachability.Found -> {
                when {
                    reachability.version == null -> stringResource(R.string.auth_found_unknown_version)
                    reachability.supported -> stringResource(R.string.auth_found, reachability.version)
                    else -> stringResource(R.string.auth_found_outdated, reachability.version)
                } to !reachability.supported
            }
        }
    if (text != null) {
        Text(
            text,
            style = Kavita.type.footnote,
            color = if (isError) colors.destructive else colors.labelSecondary,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

internal fun ServerUrlError.message(): Int =
    when (this) {
        ServerUrlError.Empty, ServerUrlError.Malformed, ServerUrlError.InvalidHost -> {
            R.string.auth_error_invalid_address
        }

        ServerUrlError.UnsupportedScheme -> {
            R.string.auth_error_scheme
        }

        ServerUrlError.ContainsCredentials -> {
            R.string.auth_error_credentials_in_url
        }

        ServerUrlError.ContainsQueryOrFragment -> {
            R.string.auth_error_query
        }

        ServerUrlError.InvalidPort -> {
            R.string.auth_error_port
        }
    }

internal fun AppError.message(): Int =
    when (this) {
        AppError.Network -> R.string.auth_error_unreachable
        AppError.Tls -> R.string.auth_error_tls
        AppError.InvalidData, AppError.NotFound -> R.string.auth_error_not_kavita
        else -> R.string.auth_error_server
    }

@Preview(name = "Connect · light", widthDp = 412, heightDp = 915)
@Composable
private fun ConnectLightPreview() = ConnectPreview(Appearance.Light)

@Preview(name = "Connect · dark", widthDp = 412, heightDp = 915)
@Composable
private fun ConnectDarkPreview() = ConnectPreview(Appearance.Dark)

@Composable
internal fun ConnectPreview(appearance: Appearance) {
    KavitaTheme(appearance) {
        ConnectScreen(
            state = ConnectUiState(reachability = previewFound, credentialsEntered = true),
            address = TextFieldState("https://kavita.home.lan:5000"),
            username = TextFieldState("aarav"),
            password = TextFieldState(),
            onStaySignedIn = {},
            onSignIn = {},
            onSso = {},
            onAuthKey = {},
        )
    }
}

private val previewFound =
    Reachability.Found(
        url = (ServerUrl.parse("https://kavita.home.lan:5000") as ServerUrlResult.Valid).url,
        version = "0.9.0.2",
        supported = true,
    )
