package com.nl.kaviyy.feature.readerepub

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.compose.AndroidFragment
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl

private val LITERATA = FontFamily("Literata")
private val INTER = FontFamily("Inter")
private const val BASE_LINE_HEIGHT = 1.7
private const val PERCENT = 100.0
private const val DEFAULT_MARGIN = 15.0

/** Imperative handle on the Readium navigator for the bottom pill's prev/next buttons. */
class EpubNavigatorHandle {
    internal var fragment: EpubNavigatorFragment? = null

    fun next() {
        fragment?.goForward()
    }

    fun previous() {
        fragment?.goBackward()
    }

    fun go(link: Link) {
        fragment?.go(link)
    }
}

@OptIn(ExperimentalReadiumApi::class)
internal fun BookSettings.toPreferences() =
    EpubPreferences(
        backgroundColor = Color(theme.colors.background.toArgb()),
        textColor = Color(theme.colors.foreground.toArgb()),
        fontFamily =
            when (font) {
                BookFont.Literata -> LITERATA
                BookFont.Inter -> INTER
                BookFont.Serif -> FontFamily.SERIF
            },
        fontSize = fontSize / PERCENT,
        lineHeight = BASE_LINE_HEIGHT * lineSpacing / PERCENT,
        pageMargins = (margin / DEFAULT_MARGIN).coerceAtLeast(0.0),
        scroll = scroll,
        // Our typography settings only apply over the publisher's CSS when this is off.
        publisherStyles = false,
    )

/**
 * Call before `super.onCreate`: a navigator restored by the FragmentManager (rotation, process death) is then a
 * harmless dummy, which [EpubNavigator] replaces with a real one for the current book.
 */
fun FragmentActivity.installBookReaderRestoreFactory() {
    supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
}

/** Remembers the navigator it created, so a restored dummy can be told apart. */
private class TrackingFactory(
    private val inner: FragmentFactory,
) : FragmentFactory() {
    var created: Fragment? = null

    override fun instantiate(
        classLoader: ClassLoader,
        className: String,
    ): Fragment = inner.instantiate(classLoader, className).also { if (it is EpubNavigatorFragment) created = it }
}

/** Hosts Readium's EPUB navigator fragment. Fonts are served from this module's assets (the bundled OFL fonts). */
@OptIn(ExperimentalReadiumApi::class)
@Composable
internal fun EpubNavigator(
    publication: Publication,
    settings: BookSettings,
    initialLocator: Locator?,
    handle: EpubNavigatorHandle,
    onTap: () -> Unit,
    onLocator: (Locator) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current.fragmentActivity() ?: return
    val tap by rememberUpdatedState(onTap)
    val locate by rememberUpdatedState(onLocator)
    val factory =
        remember(publication) {
            TrackingFactory(
                EpubNavigatorFactory(publication).createFragmentFactory(
                    initialLocator = initialLocator,
                    initialPreferences = settings.toPreferences(),
                    listener =
                        object : EpubNavigatorFragment.Listener {
                            // Links leaving the book are ignored (no browser hand-off from book content).
                            override fun onExternalLinkActivated(url: AbsoluteUrl) = Unit
                        },
                    configuration =
                        EpubNavigatorFragment.Configuration {
                            servedAssets = listOf("literata\\.ttf", "literata_italic\\.ttf", "inter\\.ttf")
                            addFontFamilyDeclaration(LITERATA) {
                                addFontFace {
                                    addSource("literata.ttf")
                                    setFontStyle(FontStyle.NORMAL)
                                }
                                addFontFace {
                                    addSource("literata_italic.ttf")
                                    setFontStyle(FontStyle.ITALIC)
                                }
                            }
                            addFontFamilyDeclaration(INTER) { addFontFace { addSource("inter.ttf") } }
                        },
                ),
            ).also {
                // AndroidFragment instantiates through the host's factory, so install this book's factory first.
                activity.supportFragmentManager.fragmentFactory = it
            }
        }
    var generation by remember { mutableIntStateOf(0) }
    var fragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    key(generation) {
        AndroidFragment<EpubNavigatorFragment>(modifier) {
            // A restored dummy: drop it and let a fresh container create the real navigator.
            if (it === factory.created) fragment = it else generation++
        }
    }
    val current = fragment ?: return
    LaunchedEffect(current) {
        handle.fragment = current
        // Edge taps turn pages; any other tap toggles the chrome.
        current.addInputListener(DirectionalNavigationAdapter(current))
        current.addInputListener(
            object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    tap()
                    return true
                }
            },
        )
        current.currentLocator.collect { locate(it) }
    }
    LaunchedEffect(current, settings) { current.submitPreferences(settings.toPreferences()) }
}

private tailrec fun Context.fragmentActivity(): FragmentActivity? =
    when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.fragmentActivity()
        else -> null
    }
