package com.nl.kaviyy.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.network.PresentedCertificate
import java.text.DateFormat

/** TOFU certificate sheet (N3) or insecure-HTTP warning (N2), depending on [ConnectUiState.sheet]. */
@Composable
internal fun TrustSheet(
    state: ConnectUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KavitaBottomSheet(onDismiss = onDismiss) { TrustSheetContent(state, onConfirm, onDismiss) }
}

@Composable
internal fun TrustSheetContent(
    state: ConnectUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val certificate = (state.reachability as? Reachability.UntrustedCertificate)?.certificate
    val isCertificate = state.sheet == ConnectSheet.Certificate && certificate != null
    // A certificate for another name can never be accepted (hostname verification stays on, N4).
    val canConfirm = !isCertificate || certificate.matchesHost
    SheetHeader(
        title = stringResource(if (isCertificate) R.string.auth_trust_title else R.string.auth_insecure_title),
        cancelLabel = stringResource(R.string.auth_cancel),
        onCancel = onDismiss,
        applyLabel =
            if (!canConfirm) {
                null
            } else {
                stringResource(
                    if (isCertificate) R.string.auth_trust_accept else R.string.auth_insecure_allow,
                )
            },
        onApply = onConfirm,
    )
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val body =
            when {
                !isCertificate -> R.string.auth_insecure_body
                canConfirm -> R.string.auth_trust_body
                else -> R.string.auth_trust_wrong_host
            }
        Text(
            stringResource(body),
            style = Kavita.type.footnote,
            color = if (canConfirm) Kavita.colors.labelSecondary else Kavita.colors.destructiveText,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (isCertificate) CertificateDetails(certificate)
    }
}

@Composable
private fun CertificateDetails(certificate: PresentedCertificate) {
    val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)
    GroupedList {
        GroupedRow(stringResource(R.string.auth_trust_subject), value = certificate.subject, divider = true)
        GroupedRow(stringResource(R.string.auth_trust_issuer), value = certificate.issuer, divider = true)
        GroupedRow(
            stringResource(R.string.auth_trust_valid),
            value = "${dates.format(certificate.notBefore)} – ${dates.format(certificate.notAfter)}",
        )
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.auth_trust_fingerprint),
            style = Kavita.type.sectionHeader,
            color = Kavita.colors.labelSecondary,
        )
        Text(certificate.fingerprint, style = Kavita.type.footnote, color = Kavita.colors.label)
    }
}
