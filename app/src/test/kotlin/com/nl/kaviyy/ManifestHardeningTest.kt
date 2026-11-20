package com.nl.kaviyy

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the M0 manifest hardening (SECURITY_CHECKLIST S4, N1, P1) against regressions. */
class ManifestHardeningTest {
    private val res = File("src/main/res/xml")
    private val android = "http://schemas.android.com/apk/res/android"
    private val backupDomains =
        setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )

    private fun parse(file: File): Element =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return List(nodes.length) { nodes.item(it) as Element }
    }

    @Test
    fun backupIsDisabledAndNoComponentButLauncherIsExported() {
        val app = parse(File("src/main/AndroidManifest.xml")).children("application").single()
        assertEquals("false", app.getAttributeNS(android, "allowBackup"))
        assertTrue(app.getAttributeNS(android, "usesCleartextTraffic").isEmpty())
        val exported =
            listOf("activity", "service", "receiver", "provider")
                .flatMap { app.children(it) }
                .filter { it.getAttributeNS(android, "exported") == "true" }
                .map { it.getAttributeNS(android, "name") }
        assertEquals(listOf(".MainActivity"), exported)
    }

    @Test
    fun networkSecurityConfigBlocksCleartextAndUserCas() {
        val base = parse(File(res, "network_security_config.xml")).children("base-config").single()
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        val anchors = base.children("certificates").map { it.getAttribute("src") }
        assertEquals(listOf("system"), anchors)
    }

    @Test
    fun dataExtractionRulesExcludeEveryDomain() {
        val rules = parse(File(res, "data_extraction_rules.xml"))
        listOf("cloud-backup", "device-transfer").forEach { section ->
            val excluded =
                rules
                    .children(section)
                    .single()
                    .children("exclude")
                    .map { it.getAttribute("domain") }
            assertEquals(backupDomains, excluded.toSet(), section)
        }
        val legacy = parse(File(res, "backup_rules.xml")).children("exclude").map { it.getAttribute("domain") }
        assertEquals(backupDomains, legacy.toSet())
    }
}
