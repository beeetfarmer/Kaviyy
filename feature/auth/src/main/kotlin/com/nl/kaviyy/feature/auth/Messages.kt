package com.nl.kaviyy.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlError
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.SsoInfo

@Composable
internal fun reachabilityMessage(reachability: Reachability): Pair<String?, Boolean> =
    when (reachability) {
        Reachability.Idle -> {
            null to false
        }

        Reachability.Checking -> {
            stringResource(R.string.auth_checking) to false
        }

        is Reachability.InsecureHttp -> {
            stringResource(R.string.auth_insecure_http) to false
        }

        Reachability.HttpNotAllowed -> {
            stringResource(R.string.auth_http_public) to true
        }

        is Reachability.UntrustedCertificate -> {
            stringResource(R.string.auth_untrusted_certificate) to true
        }

        is Reachability.PinMismatch -> {
            stringResource(R.string.auth_pin_mismatch) to true
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
                reachability.supported -> stringResource(R.string.auth_found, reachability.version.orEmpty())
                else -> stringResource(R.string.auth_found_outdated, reachability.version.orEmpty())
            } to !reachability.supported
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
