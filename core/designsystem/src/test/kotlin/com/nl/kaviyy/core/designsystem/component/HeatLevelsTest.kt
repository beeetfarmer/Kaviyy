package com.nl.kaviyy.core.designsystem.component

import org.junit.Test
import kotlin.test.assertEquals

class HeatLevelsTest {
    @Test
    fun levelsAreQuartilesOfTheBusiestDayAndZeroForNone() {
        assertEquals(listOf(0, 1, 2, 3, 4, 1), heatLevels(listOf(0, 1, 4, 6, 8, 2)))
        assertEquals(listOf(0, 0), heatLevels(listOf(0, 0)))
    }
}
