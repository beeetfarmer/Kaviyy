package com.nl.kaviyy.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

/** Loading, empty and error states (DESIGN.md §8: skeletons, inline errors with Retry). */
@Composable
internal fun Skeleton(grid: Boolean) {
    val shape = if (grid) KavitaShapes.coverGrid else KavitaShapes.rowThumb
    val m = if (grid) Modifier.aspectRatio(2f / 3f) else Modifier.height(72.dp)
    Box(m.fillMaxWidth().background(Kavita.colors.surface, shape))
}

@Composable
internal fun Message(text: String) {
    Text(
        text,
        style = Kavita.type.callout,
        color = Kavita.colors.labelSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

@Composable
internal fun NoLibraries() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.library_no_libraries_title),
            style = Kavita.type.headline,
            color = Kavita.colors.label,
        )
        Message(stringResource(R.string.library_no_libraries_body))
    }
}

@Composable
internal fun ErrorBlock(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Message(stringResource(R.string.library_error_body))
        PrimaryButton(stringResource(R.string.library_retry), onRetry)
    }
}
