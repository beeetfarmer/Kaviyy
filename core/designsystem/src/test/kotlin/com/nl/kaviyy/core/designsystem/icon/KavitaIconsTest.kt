package com.nl.kaviyy.core.designsystem.icon

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.addPathNodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KavitaIconsTest {
    @Test
    fun everyIconIsA24UnitVectorWithPaths() {
        assertEquals(34, KavitaIcons.all.size)
        assertEquals(
            KavitaIcons.all.size,
            KavitaIcons.all
                .map { it.name }
                .toSet()
                .size,
            "icon names must be unique",
        )
        KavitaIcons.all.forEach { icon ->
            assertEquals(24f, icon.viewportWidth, icon.name)
            assertEquals(24f, icon.viewportHeight, icon.name)
            val paths = icon.root.filterIsInstance<VectorPath>()
            assertTrue(paths.isNotEmpty() && paths.all { it.pathData.isNotEmpty() }, icon.name)
        }
    }

    @Test
    fun tabIconsUseTheTabBarStroke() {
        listOf(KavitaIcons.Home, KavitaIcons.Library, KavitaIcons.Bookmark, KavitaIcons.User).forEach { icon ->
            assertTrue(icon.root.filterIsInstance<VectorPath>().all { it.strokeLineWidth == 1.8f }, icon.name)
        }
    }

    @Test
    fun circleHelperDrawsTwoArcsAroundTheCentre() {
        val nodes = addPathNodes(circle(12f, 8f, 4f))
        assertEquals(PathNode.MoveTo(8f, 8f), nodes.first())
        assertEquals(2, nodes.count { it is PathNode.RelativeArcTo })
        assertEquals(PathNode.Close, nodes.last())
    }

    @Test
    fun rectHelperClosesWithFourRoundedCorners() {
        val nodes = addPathNodes(rect(3f, 4f, 5f, 16f, 1f))
        assertEquals(PathNode.MoveTo(4f, 4f), nodes.first())
        assertEquals(4, nodes.count { it is PathNode.RelativeArcTo })
        // Edges: 5 - 2 = 3 wide, 16 - 2 = 14 tall.
        assertTrue(PathNode.RelativeHorizontalTo(3f) in nodes)
        assertTrue(PathNode.RelativeVerticalTo(14f) in nodes)
    }
}
