package com.nl.kaviyy.feature.readerepub

import android.webkit.WebView
import android.widget.FrameLayout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class WebViewHardeningTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun pageWebViewsLoseFileAndContentAccess() {
        val web = WebView(context)
        val tree = FrameLayout(context).apply { addView(FrameLayout(context).apply { addView(web) }) }
        assertEquals(listOf(web), tree.webViews())
        tree.webViews().forEach { it.harden() }
        assertFalse(web.settings.allowFileAccess)
        assertFalse(web.settings.allowContentAccess)
    }
}
