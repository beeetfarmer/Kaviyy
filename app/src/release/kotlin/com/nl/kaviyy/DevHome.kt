package com.nl.kaviyy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme

/** Release placeholder until the Connect screen (M3); the component catalogue is debug-only. */
@Composable
internal fun DevHome() {
    KavitaTheme {
        Box(Modifier.fillMaxSize().background(Kavita.colors.background), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.app_name), style = Kavita.type.largeTitle, color = Kavita.colors.label)
        }
    }
}
