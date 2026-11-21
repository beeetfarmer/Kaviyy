package com.nl.kaviyy.feature.readerepub

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * SECURITY_CHECKLIST P6. Readium serves the book through request interception, so the page WebViews never need
 * file:// or content:// access. (File access from file URLs and universal access are off by default since API 16.)
 */
internal fun WebView.harden() {
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.safeBrowsingEnabled = true
}

internal fun View.webViews(): List<WebView> =
    when (this) {
        is WebView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).webViews() }
        else -> emptyList()
    }

/** Hardens the WebView of every page fragment Readium creates inside the navigator. */
internal object WebViewHardening : FragmentManager.FragmentLifecycleCallbacks() {
    override fun onFragmentViewCreated(
        fm: FragmentManager,
        f: Fragment,
        v: View,
        savedInstanceState: Bundle?,
    ) = v.webViews().forEach { it.harden() }
}
