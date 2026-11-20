package com.nl.kaviyy.feature.home

import kotlin.math.roundToInt

private const val PERCENT = 100

internal fun percent(progress: Float): Int = (progress * PERCENT).roundToInt()

/** Avatar initials: first letters of the first two words, else the first two characters. */
internal fun initials(name: String?): String {
    val words = name.orEmpty().split(' ', '.', '_', '-').filter { it.isNotBlank() }
    val raw = if (words.size >= 2) "${words[0][0]}${words[1][0]}" else words.firstOrNull()?.take(2).orEmpty()
    return raw.uppercase()
}
