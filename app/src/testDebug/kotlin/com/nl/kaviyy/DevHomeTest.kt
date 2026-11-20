package com.nl.kaviyy

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevHomeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun catalogueSwitchesAppearanceAndAccent() {
        compose.setContent { DevHome() }
        compose.onAllNodesWithText("System").onFirst().assertIsSelected()
        compose.onNodeWithText("Dark").performClick().assertIsSelected()
        compose.onNodeWithText("Ocean").performClick().assertIsSelected()
        compose.onNodeWithText("Sign In").assertExists()
    }
}
