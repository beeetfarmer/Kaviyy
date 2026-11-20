package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun circleIconButtonIsLabelledClickableAnd48dpTouchTarget() {
        var clicks = 0
        compose.setContent { KavitaTheme { CircleIconButton(KavitaIcons.More, "More options", { clicks++ }) } }
        compose
            .onNodeWithContentDescription("More options")
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
            .assert(hasRole(Role.Button))
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledPrimaryButtonIgnoresClicks() {
        var clicks = 0
        compose.setContent { KavitaTheme { PrimaryButton("Sign In", { clicks++ }, enabled = false) } }
        compose.onNodeWithText("Sign In").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun segmentedControlSelectsTabs() {
        var selected by mutableIntStateOf(0)
        compose.setContent { KavitaTheme { SegmentedControl(listOf("Manga", "Comics"), selected, { selected = it }) } }
        compose.onNodeWithText("Manga").assertIsSelected()
        compose
            .onNodeWithText("Comics")
            .assertIsNotSelected()
            .assert(hasRole(Role.Tab))
            .performClick()
        assertEquals(1, selected)
        compose.onNodeWithText("Comics").assertIsSelected()
    }

    @Test
    fun filterChipExposesSelection() {
        compose.setContent {
            KavitaTheme {
                FilterChip("Filter · 2", selected = true, onClick = {})
                FilterChip("Unread", selected = false, onClick = {})
            }
        }
        compose.onNodeWithText("Filter · 2").assertIsSelected()
        compose.onNodeWithText("Unread").assertIsNotSelected()
    }

    @Test
    fun switchTogglesWithSwitchRole() {
        compose.setContent {
            var on by remember { mutableStateOf(false) }
            KavitaTheme { KavitaSwitch(on, { on = it }, contentDescription = "Stay signed in") }
        }
        compose
            .onNodeWithContentDescription("Stay signed in")
            .assertIsOff()
            .assert(hasRole(Role.Switch))
            .performClick()
            .assertIsOn()
    }

    @Test
    fun groupedInputRowShowsPlaceholderAndReportsInput() {
        val state = TextFieldState()
        compose.setContent { KavitaTheme { GroupedInputRow("Username", state, placeholder = "Required") } }
        compose.onNodeWithText("Required").assertExists()
        compose.onNodeWithContentDescription("Username").performTextInput("aarav")
        assertEquals("aarav", state.text.toString())
        compose.onNodeWithText("Required").assertDoesNotExist()
    }

    @Test
    fun secureInputRowMasksText() {
        val state = TextFieldState()
        compose.setContent { KavitaTheme { GroupedInputRow("Password", state, secure = true) } }
        compose
            .onNodeWithContentDescription("Password")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .performTextInput("hunter2")
        assertEquals("hunter2", state.text.toString())
    }

    @Test
    fun groupedSwitchRowTogglesThroughItsSwitch() {
        var checked by mutableStateOf(true)
        compose.setContent { KavitaTheme { GroupedSwitchRow("Stay signed in", checked, { checked = it }) } }
        compose.onNodeWithContentDescription("Stay signed in").performClick()
        assertEquals(false, checked)
    }

    @Test
    fun progressBarClampsAndReportsProgress() {
        compose.setContent { KavitaTheme { ProgressBar(1.7f) } }
        compose
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(
                        1f,
                        0f..1f,
                    ),
                ),
            )
    }

    @Test
    fun avatarShowsTwoUppercaseInitials() {
        compose.setContent { KavitaTheme { Avatar("amx", contentDescription = "Your profile") } }
        compose.onNodeWithText("AM").assertExists()
        compose.onNodeWithContentDescription("Your profile").assertExists()
    }

    @Test
    fun serverPillAndSettingsRowAreClickable() {
        var pill = 0
        var row = 0
        compose.setContent {
            KavitaTheme {
                androidx.compose.foundation.layout.Column {
                    ServerPill("Home Server", online = true, onClick = { pill++ })
                    SettingsRow(
                        KavitaIcons.Moon,
                        androidx.compose.ui.graphics.Color.Black,
                        "Theme",
                        { row++ },
                        value = "System",
                    )
                }
            }
        }
        compose.onNodeWithText("Home Server").performClick()
        compose.onNodeWithText("Theme").performClick()
        assertEquals(1, pill)
        assertEquals(1, row)
    }

    @Test
    fun showcaseRendersInBothThemes() {
        compose.setContent {
            KavitaTheme(
                com.nl.kaviyy.core.designsystem.theme.Appearance.Dark,
            ) { ComponentShowcase() }
        }
        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText("Sign In").assertExists()
    }
}
