package com.nl.kaviyy.feature.readerepub

import com.nl.kaviyy.core.data.ReaderDisplay
import com.nl.kaviyy.core.designsystem.theme.ReaderTheme
import com.nl.kaviyy.core.designsystem.theme.ReaderThemes
import com.nl.kaviyy.core.network.ReadingProfileDto

/** Paper/White/Night (DESIGN.md §2.3) ↔ Kavita's book themes ("Black" also reads as Night). */
enum class BookTheme(
    val colors: ReaderTheme,
    val kavita: String,
) {
    Paper(ReaderThemes.Paper, "Paper"),
    White(ReaderThemes.White, "White"),
    Night(ReaderThemes.Night, "Dark"),
    ;

    companion object {
        fun fromKavita(name: String): BookTheme =
            when (name) {
                "White" -> White
                "Dark", "Black" -> Night
                else -> Paper
            }
    }
}

/** The "Aa" sheet's fonts [derived]; Readium serves Literata and Inter from assets. */
enum class BookFont(
    val key: String,
) {
    Literata("literata"),
    Inter("inter"),
    Serif("serif"),
    ;

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: Literata
    }
}

/** Kavita `BookPageLayoutMode`: 0 scroll, 1/2 one or two columns (paged). */
private const val LAYOUT_SCROLL = 0
private const val LAYOUT_PAGED = 1
const val FONT_SIZE_MIN = 50
const val FONT_SIZE_MAX = 300
const val LINE_SPACING_MIN = 80
const val LINE_SPACING_MAX = 200
const val MARGIN_MAX = 30

/** "Aa" settings: theme, size, line height, margins and layout live in the Kavita profile; font and brightness here. */
data class BookSettings(
    val theme: BookTheme = BookTheme.Paper,
    /** Percent, Kavita `bookReaderFontSize`. */
    val fontSize: Int = 100,
    /** Percent, Kavita `bookReaderLineSpacing`. */
    val lineSpacing: Int = 100,
    /** Kavita `bookReaderMargin` (0–30). */
    val margin: Int = 15,
    val scroll: Boolean = false,
    val font: BookFont = BookFont.Literata,
    val keepScreenOn: Boolean = true,
    val brightness: Float? = null,
)

internal fun bookSettings(
    profile: ReadingProfileDto,
    device: ReaderDisplay,
) = BookSettings(
    theme = BookTheme.fromKavita(profile.bookReaderThemeName),
    fontSize = profile.bookReaderFontSize.coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX),
    lineSpacing = profile.bookReaderLineSpacing.coerceIn(LINE_SPACING_MIN, LINE_SPACING_MAX),
    margin = profile.bookReaderMargin.coerceIn(0, MARGIN_MAX),
    scroll = profile.bookReaderLayoutMode == LAYOUT_SCROLL,
    font = BookFont.fromKey(device.bookFont),
    keepScreenOn = device.keepScreenOn,
    brightness = device.brightness,
)

/** Writes the account half back; keeps a two-column choice made on the web when staying paged. */
internal fun ReadingProfileDto.with(settings: BookSettings) =
    copy(
        bookReaderThemeName = settings.theme.kavita,
        bookReaderFontSize = settings.fontSize,
        bookReaderLineSpacing = settings.lineSpacing,
        bookReaderMargin = settings.margin,
        bookReaderLayoutMode =
            when {
                settings.scroll -> LAYOUT_SCROLL
                bookReaderLayoutMode == LAYOUT_SCROLL -> LAYOUT_PAGED
                else -> bookReaderLayoutMode
            },
    )
