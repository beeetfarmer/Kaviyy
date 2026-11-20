package com.nl.kaviyy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Placeholder until the design system (M1) and Connect screen (M3) land.
        setContent {
            KavitaTheme {
                Box(Modifier.fillMaxSize().background(Kavita.colors.background), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.app_name), style = Kavita.type.largeTitle, color = Kavita.colors.label)
                }
            }
        }
    }
}
