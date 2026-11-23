package com.nl.kaviyy.feature.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.HomeItem
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class SensitiveContentTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val state =
        SensitiveContentUiState(
            AppSettings(blurTags = listOf("Ecchi"), hideTags = listOf("Mature")).withUnhidden(1, 2, show = true),
            serverId = 1,
            hidden = listOf(HomeItem(1, 0, "Crimson Night", 1, 0), HomeItem(2, 0, "Ember Road", 1, 0)),
            tagNames = listOf("Action", "Ecchi", "Mature"),
        )

    @Test
    fun togglingIgnoresCase() {
        assertEquals(listOf("Ecchi"), emptyList<String>().toggled("Ecchi"))
        assertEquals(emptyList(), listOf("Ecchi").toggled("ECCHI"))
        assertEquals(listOf("Ecchi", "Gore"), listOf("Ecchi").toggled("Gore"))
    }

    @Test
    fun genresAndTagsMergeIntoOneSortedList() {
        assertEquals(
            listOf("Action", "ecchi", "Mature"),
            mergeTagNames(listOf("ecchi", "Mature", " "), listOf("Ecchi", "Action")),
        )
    }

    @Test
    fun hiddenUnlessUnhiddenOnThisServer() {
        assertTrue(state.isHidden(1))
        assertFalse(state.isHidden(2))
        assertTrue(state.copy(serverId = 5).isHidden(2))
    }

    @Test
    fun hiddenSeriesSwitchAndTagPicker() {
        val hidden = mutableListOf<Pair<Int, Boolean>>()
        val toggled = mutableListOf<Pair<TagKind, String>>()
        compose.setContent {
            KavitaTheme {
                SensitiveContentScreen(
                    state,
                    onBack = {},
                    onOpenPicker = {},
                    onToggleTag = { k, n -> toggled += k to n },
                    onBlurRadius = {},
                    onHidden = { id, h -> hidden += id to h },
                )
            }
        }
        compose.onNodeWithContentDescription("Crimson Night").performClick()
        assertEquals(listOf(1 to false), hidden)
        compose.onNodeWithText("Hide from Home").performClick()
        compose.onNodeWithText("Action").performClick()
        assertEquals(listOf(TagKind.Hide to "Action"), toggled)
    }

    @Test
    fun screenshotSensitiveLight() = shot("sensitive_light", Appearance.Light)

    @Test
    fun screenshotSensitiveDark() = shot("sensitive_dark", Appearance.Dark)

    private fun shot(
        name: String,
        appearance: Appearance,
    ) {
        compose.setContent {
            KavitaTheme(appearance = appearance) {
                SensitiveContentScreen(state, {}, {}, { _, _ -> }, {}, { _, _ -> })
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
