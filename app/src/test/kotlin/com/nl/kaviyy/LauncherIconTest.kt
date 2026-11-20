package com.nl.kaviyy

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DESIGN.md §5: adaptive launcher icon in the accent color with a monochrome layer. */
class LauncherIconTest {
    private val res = File("src/main/res")
    private val android = "http://schemas.android.com/apk/res/android"

    private fun parse(path: String) =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(res, path))
            .documentElement

    @Test
    fun adaptiveIconsHaveAllThreeLayers() {
        listOf("ic_launcher", "ic_launcher_round").forEach { name ->
            val icon = parse("mipmap-anydpi-v26/$name.xml")
            listOf("background", "foreground", "monochrome").forEach { layer ->
                val drawable = (icon.getElementsByTagName(layer).item(0) as org.w3c.dom.Element)
                assertTrue(drawable.getAttributeNS(android, "drawable").isNotEmpty(), "$name/$layer")
            }
        }
    }

    @Test
    fun backgroundIsTheKavitaGreenAccent() {
        val color = parse("values/ic_launcher_colors.xml").getElementsByTagName("color").item(0)
        assertEquals("#187A4F", color.textContent.trim())
    }
}
