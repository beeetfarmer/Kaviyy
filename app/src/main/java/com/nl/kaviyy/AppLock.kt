package com.nl.kaviyy

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.theme.Kavita

/**
 * [derived] App lock: the system's confirm-credential screen (PIN, pattern, password or biometric). It needs no
 * permission, unlike BiometricPrompt. No secure lock screen → nothing to check, so the app opens.
 */
@Composable
internal fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.lock_prompt)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) onUnlocked()
        }
    val unlock = {
        @Suppress("DEPRECATION") // replaced by BiometricPrompt, which needs USE_BIOMETRIC
        val intent =
            context
                .getSystemService(
                    KeyguardManager::class.java,
                )?.createConfirmDeviceCredentialIntent(title, null)
        if (intent == null) onUnlocked() else launcher.launch(intent)
    }
    LaunchedEffect(Unit) { unlock() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Kavita.colors.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = Kavita.type.largeTitle, color = Kavita.colors.label)
        Text(stringResource(R.string.lock_locked), style = Kavita.type.callout, color = Kavita.colors.labelSecondary)
        PrimaryButton(stringResource(R.string.lock_unlock), unlock, Modifier.fillMaxWidth())
    }
}

/** Blanks the Recents thumbnail; before Android 13 that needs FLAG_SECURE, which also blocks screenshots. */
@Composable
internal fun HideInRecents(hide: Boolean) {
    val activity = LocalActivity.current ?: return
    LaunchedEffect(hide) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
