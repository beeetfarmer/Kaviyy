package com.nl.kaviyy.feature.readerepub

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.theme.Kavita

/** P6 [derived]: a link in the book opens in the browser only after the user sees where it goes. */
@Composable
internal fun LinkSheet(
    url: String,
    onClose: () -> Unit,
) = KavitaBottomSheet(onDismiss = onClose) {
    val context = LocalContext.current
    SheetHeader(stringResource(R.string.epub_link_title), stringResource(R.string.epub_cancel), onClose)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(url.toUri().host.orEmpty(), style = Kavita.type.headline, color = Kavita.colors.label)
        Text(url, style = Kavita.type.footnote, color = Kavita.colors.labelSecondary, maxLines = 3)
        PrimaryButton(
            stringResource(R.string.epub_link_open),
            {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
                runCatching { context.startActivity(intent) }
                onClose()
            },
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}
