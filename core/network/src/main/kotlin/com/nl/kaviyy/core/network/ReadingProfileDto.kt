package com.nl.kaviyy.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Typed view of Kavita's UserReadingProfileDto: the reader settings the app uses (Kavita enum ints). The full server
 * object is kept as JSON alongside, and [mergeInto] writes only these fields back, so settings this app doesn't know
 * survive a round trip. Defaults mirror Kavita's.
 */
@Serializable
data class ReadingProfileDto(
    val id: Int = 0,
    val name: String? = null,
    /** `ReadingProfileKind`: 0 Default, 1 User, 2 Implicit (per series). */
    val kind: Int = 0,
    // Image reader
    val readingDirection: Int = 0,
    val scalingOption: Int = 3,
    val pageSplitOption: Int = 3,
    val readerMode: Int = 0,
    val layoutMode: Int = 1,
    val backgroundColor: String = "#000000",
    val autoCloseMenu: Boolean = true,
    val showScreenHints: Boolean = true,
    val emulateBook: Boolean = false,
    val swipeToPaginate: Boolean = true,
    // Book reader
    val bookReaderFontSize: Int = 100,
    val bookReaderLineSpacing: Int = 100,
    val bookReaderMargin: Int = 15,
    val bookReaderFontFamily: String = "default",
    val bookReaderTapToPaginate: Boolean = false,
    val bookReaderReadingDirection: Int = 0,
    val bookReaderWritingStyle: Int = 0,
    val bookReaderThemeName: String = "Dark",
    val bookReaderLayoutMode: Int = 0,
    val bookReaderImmersiveMode: Boolean = false,
    // PDF reader
    val pdfTheme: Int = 0,
    val pdfScrollMode: Int = 0,
    val pdfSpreadMode: Int = 0,
) {
    /** [raw] with this profile's settings written over it; unknown server fields are kept. */
    fun mergeInto(raw: JsonObject): JsonObject = JsonObject(raw + profileJson.encodeToJsonElement(this).jsonObject)

    companion object {
        fun parse(raw: String): ReadingProfileDto = profileJson.decodeFromString(raw)

        fun rawObject(raw: String): JsonObject = profileJson.parseToJsonElement(raw).jsonObject
    }
}

internal val profileJson =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }
