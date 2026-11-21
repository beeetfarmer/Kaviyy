package com.nl.kaviyy.feature.downloads

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme

@Composable
internal fun DownloadSettingsPreview(
    appearance: Appearance,
    settings: AppSettings = AppSettings(autoDownloadCount = 2, autoDeleteAfterDays = 1, storageLimitMb = 5 * 1024),
    actions: DownloadSettingsActions = DownloadSettingsActions(),
) = KavitaTheme(appearance = appearance) { DownloadSettingsScreen(settings, actions) }

@Preview(name = "Download settings · light", heightDp = 1400)
@Composable
private fun SettingsLight() = DownloadSettingsPreview(Appearance.Light)

@Preview(name = "Download settings · dark", heightDp = 1400)
@Composable
private fun SettingsDark() = DownloadSettingsPreview(Appearance.Dark)
