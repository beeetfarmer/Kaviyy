package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.designsystem.theme.SettingsTileColors

/**
 * Every core component in its main states, using sample copy from the design canvas.
 * Used by the @Previews below, the debug component catalogue and screenshot tests.
 */
@Composable
fun ComponentShowcase(modifier: Modifier = Modifier) {
    var library by remember { mutableIntStateOf(0) }
    var staySignedIn by remember { mutableStateOf(true) }
    val username = rememberTextFieldState("aarav")
    val password = rememberTextFieldState()
    Column(
        modifier = modifier.background(Kavita.colors.background).padding(KavitaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LargeTitleHeader(title = "Home", overline = "Friday, 20 November") {
            ServerPill(name = "Home Server", online = true, onClick = {})
            Avatar(initials = "AM", contentDescription = "Your profile", onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleIconButton(KavitaIcons.Grid, "Switch to list view", {})
            CircleIconButton(KavitaIcons.More, "More options", {})
            CircleIconButton(
                KavitaIcons.BookmarkFilled,
                "Remove from Want to Read",
                {},
                style = CircleIconButtonStyle.Action,
                containerColor = Kavita.colors.accentContainer,
            )
            CircleIconButton(KavitaIcons.Download, "Download for offline", {}, style = CircleIconButtonStyle.Action)
            Box(Modifier.clip(KavitaShapes.pill).background(Color(0xFFB4441B)).padding(4.dp)) {
                CircleIconButton(KavitaIcons.ChevronLeft, "Back", {}, style = CircleIconButtonStyle.Translucent)
            }
        }
        SegmentedControl(listOf("Manga", "Comics", "Books", "Light Novels"), library, { library = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("Filter · 2", selected = true, onClick = {}, leadingIcon = KavitaIcons.Filter)
            FilterChip("Recently Added", selected = false, onClick = {}, dropdown = true)
            FilterChip("Unread", selected = false, onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip("Ongoing", style = StatusChipStyle.Accent)
            StatusChip("2019")
            StatusChip("4.6", leadingIcon = KavitaIcons.Star, leadingIconTint = Kavita.colors.rating)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Account")
            GroupedList {
                GroupedInputRow("Username", username, divider = true)
                GroupedInputRow(
                    "Password",
                    password,
                    placeholder = "Required",
                    secure = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
            GroupedList { GroupedSwitchRow("Stay signed in", staySignedIn, { staySignedIn = it }) }
        }
        GroupedList {
            GroupedThumbRow(
                "Volume 5",
                "Ch. 41–50 · 212 pages",
                state = "62%",
                stateColor = Kavita.colors.accent,
                onClick = {},
                divider = true,
            ) {
                Box(Modifier.size(40.dp, 60.dp).clip(KavitaShapes.rowThumb).background(Color(0xFF8E3414)))
            }
            GroupedThumbRow("Volume 4", "Ch. 31–40 · 198 pages", state = "Read", onClick = {}) {
                Box(Modifier.size(40.dp, 60.dp).clip(KavitaShapes.rowThumb).background(Color(0xFFA23C17)))
            }
        }
        GroupedList {
            SettingsRow(
                KavitaIcons.Book,
                SettingsTileColors.reader,
                "Reader Preferences",
                {},
                value = "RTL",
                divider = true,
            )
            SettingsRow(KavitaIcons.Moon, SettingsTileColors.theme, "Theme", {}, value = "System", divider = true)
            SettingsRow(KavitaIcons.Download, SettingsTileColors.downloads, "Downloads", {}, value = "2.1 GB")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleGlyphIcon(CircleGlyph.Clear)
            CircleGlyphIcon(CircleGlyph.Remove)
            CircleGlyphIcon(CircleGlyph.Add)
            KavitaSwitch(checked = false, onCheckedChange = {})
        }
        ProgressBar(0.62f, height = 6.dp)
        PrimaryButton("Sign In", {}, Modifier.fillMaxWidth())
        SecondaryButton("Continue with SSO", {}, Modifier.fillMaxWidth(), leadingIcon = KavitaIcons.Lock)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Resume", {}, size = ButtonSize.Small, leadingIcon = KavitaIcons.Play)
            PrimaryButton("Continue Ch. 49", {}, size = ButtonSize.Medium, leadingIcon = KavitaIcons.Play)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Preview(name = "Components · light", widthDp = 412, heightDp = 1500)
@Composable
private fun ComponentsLightPreview() = KavitaTheme(Appearance.Light) { ComponentShowcase(Modifier.fillMaxWidth()) }

@Preview(name = "Components · dark", widthDp = 412, heightDp = 1500)
@Composable
private fun ComponentsDarkPreview() = KavitaTheme(Appearance.Dark) { ComponentShowcase(Modifier.fillMaxWidth()) }
