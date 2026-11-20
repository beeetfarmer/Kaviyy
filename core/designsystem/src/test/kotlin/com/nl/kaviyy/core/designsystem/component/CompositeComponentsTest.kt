package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CompositeComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun badgeTextCapsAt99() {
        assertEquals("3", badgeText(3))
        assertEquals("99", badgeText(99))
        assertEquals("99+", badgeText(140))
    }

    @Test
    fun coverColorIsStablePerSeriesAndHandlesAnyId() {
        assertEquals(coverColorFor(42), coverColorFor(42))
        assertEquals(CoverPalette[0], coverColorFor(0))
        assertEquals(coverColorFor(10), coverColorFor(0))
        assertEquals(CoverPalette[9], coverColorFor(-1))
    }

    @Test
    fun barFractionsAreRelativeToThePeak() {
        assertEquals(listOf(0.5f, 1f, 0.25f), barFractions(listOf(40f, 80f, 20f)))
        assertEquals(listOf(0f, 0f), barFractions(listOf(0f, 0f)))
        assertEquals(emptyList(), barFractions(emptyList()))
    }

    @Test
    fun coverCardShowsBadgesAndClicks() {
        var clicks = 0
        compose.setContent {
            KavitaTheme {
                CoverCard(
                    "Paper Moths",
                    "4 volumes",
                    Color.Blue,
                    { clicks++ },
                    unreadCount = 140,
                    downloadedDescription = "Downloaded",
                )
            }
        }
        compose.onNodeWithText("99+").assertExists()
        compose.onNodeWithContentDescription("Downloaded").assertExists()
        compose.onNodeWithText("4 volumes").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun continueReadingCardResumes() {
        var resumed = false
        compose.setContent {
            KavitaTheme {
                ContinueReadingCard(
                    "Continue reading",
                    "Ember Road",
                    "Volume 5 · Chapter 49",
                    Color.Red,
                    0.62f,
                    "14 pages left · about 6 min",
                    "Resume",
                    { resumed = true },
                )
            }
        }
        compose.onNodeWithText("CONTINUE READING").assertExists()
        compose.onNodeWithText("Resume").performClick()
        assertEquals(true, resumed)
    }

    @Test
    fun floatingTabBarSelectsTabsAndOpensSearch() {
        var selected = -1
        var searched = false
        compose.setContent {
            KavitaTheme {
                FloatingTabBar(
                    items = listOf(TabItem("Home", KavitaIcons.Home), TabItem("Library", KavitaIcons.Library)),
                    selectedIndex = 0,
                    onSelect = { selected = it },
                    searchLabel = "Search",
                    onSearch = { searched = true },
                )
            }
        }
        compose.onNodeWithText("Home").assertIsSelected()
        compose
            .onNodeWithText("Library")
            .assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .performClick()
        compose.onNodeWithContentDescription("Search").performClick()
        assertEquals(1, selected)
        assertEquals(true, searched)
    }

    @Test
    fun sheetHeaderButtonsWork() {
        var cancelled = false
        var applied = false
        compose.setContent {
            KavitaTheme {
                SheetHeader("Filter", "Cancel", { cancelled = true }, applyLabel = "Apply", onApply = {
                    applied =
                        true
                })
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(true, cancelled)
        assertEquals(true, applied)
    }

    @Test
    fun highlightToolbarSelectsColorsAndActions() {
        var color = -1
        var copied = false
        compose.setContent {
            KavitaTheme {
                HighlightToolbar(listOf("Yellow", "Green", "Blue", "Pink"), 0, { color = it }, "Note", {}, "Copy", {
                    copied =
                        true
                })
            }
        }
        compose.onNodeWithContentDescription("Yellow").assertIsSelected()
        compose.onNodeWithContentDescription("Blue").performClick()
        compose.onNodeWithText("Copy").performClick()
        assertEquals(2, color)
        assertEquals(true, copied)
    }

    @Test
    fun statBarsExposeOneAccessibleSummary() {
        compose.setContent {
            KavitaTheme {
                Column { StatBars(listOf(1f, 3f), listOf("M", "T"), contentDescription = "Minutes read per day") }
            }
        }
        compose.onNodeWithContentDescription("Minutes read per day").assertExists()
        compose.onNodeWithText("M").assertDoesNotExist()
    }
}
