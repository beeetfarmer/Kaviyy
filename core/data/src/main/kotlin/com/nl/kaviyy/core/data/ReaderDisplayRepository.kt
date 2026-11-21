package com.nl.kaviyy.core.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Reader settings that belong to this device, not to the Kavita account. */
data class ReaderDisplay(
    val keepScreenOn: Boolean = true,
    /** 0..1; null follows the system. */
    val brightness: Float? = null,
    /** EPUB font key (the design's Literata / Inter / serif are not Kavita font names). */
    val bookFont: String = BOOK_FONT_DEFAULT,
)

const val BOOK_FONT_DEFAULT = "literata"

interface ReaderDisplayRepository {
    val display: Flow<ReaderDisplay>

    fun update(change: (ReaderDisplay) -> ReaderDisplay)
}

/** Plain SharedPreferences: two non-sensitive device prefs (credentials live in the Tink-encrypted DataStore). */
@Singleton
class SharedPrefsReaderDisplayRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ReaderDisplayRepository {
        private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        private fun read() =
            ReaderDisplay(
                keepScreenOn = prefs.getBoolean(KEEP_ON, true),
                brightness = prefs.getFloat(BRIGHTNESS, -1f).takeIf { it >= 0f },
                bookFont = prefs.getString(BOOK_FONT, null) ?: BOOK_FONT_DEFAULT,
            )

        override val display: Flow<ReaderDisplay> =
            callbackFlow {
                trySend(read())
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

        override fun update(change: (ReaderDisplay) -> ReaderDisplay) {
            val display = change(read())
            prefs
                .edit()
                .putBoolean(KEEP_ON, display.keepScreenOn)
                .putFloat(BRIGHTNESS, display.brightness?.coerceIn(0f, 1f) ?: -1f)
                .putString(BOOK_FONT, display.bookFont)
                .apply()
        }

        internal companion object {
            /** Deleted by Delete Local Data. */
            const val FILE = "reader_display"
            private const val KEEP_ON = "keep_screen_on"
            private const val BRIGHTNESS = "brightness"
            private const val BOOK_FONT = "book_font"
        }
    }
