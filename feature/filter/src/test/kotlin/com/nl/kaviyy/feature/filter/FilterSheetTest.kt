package com.nl.kaviyy.feature.filter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class FilterSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsRulesInCanvasWordingAndReportsActions() {
        var removed = -1
        var added = 0
        var any: Boolean? = null
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                Column {
                    FilterContent(
                        previewFilter,
                        previewLibraries,
                        TextFieldState(),
                        FilterActions(
                            onRemoveRule = { removed = it },
                            onAddRule = { added++ },
                            onMatchAll = { any = !it },
                        ),
                    )
                }
            }
        }
        compose.onNodeWithText("Genre · is any of").assertExists()
        compose.onNodeWithText("Fantasy, Adventure").assertExists()
        compose.onNodeWithText("Read Progress · less than").assertExists()
        compose.onNodeWithText("100%").assertExists()
        compose.onNodeWithText("Release Year · after").assertExists()
        compose.onNodeWithText("Age Rating · at most").assertExists()
        compose.onNodeWithText("Last chapter added ↓").assertExists()
        compose.onNodeWithText("Manga, Comics").assertExists()
        compose.onAllNodesWithContentDescription("Remove rule").onFirst().performClick()
        assertEquals(0, removed)
        compose.onNodeWithText("Add Rule").performClick()
        assertEquals(1, added)
        compose.onNodeWithText("Any rule").performClick()
        assertEquals(true, any)
    }

    @Test
    fun screenshotLight() = shot("filter_light") { FilterPreview(Appearance.Light) }

    @Test
    fun screenshotDark() = shot("filter_dark") { FilterPreview(Appearance.Dark) }

    @Test
    fun screenshotRule() = shot("filter_rule_light") { FilterPreview(Appearance.Light, previewRule) }

    private fun shot(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
