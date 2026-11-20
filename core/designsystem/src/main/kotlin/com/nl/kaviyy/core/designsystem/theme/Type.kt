@file:Suppress("MagicNumber")

package com.nl.kaviyy.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nl.kaviyy.core.designsystem.R

// Bundled variable fonts (OFL, see core/designsystem/licenses). No network font fetch (MASVS V1).

private fun variable(
    res: Int,
    weight: FontWeight,
    style: FontStyle = FontStyle.Normal,
) = Font(res, weight, style, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private val uiWeights =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)

val Inter = FontFamily(uiWeights.map { variable(R.font.inter, it) })

val Literata =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold).map { variable(R.font.literata, it) } +
            variable(R.font.literata_italic, FontWeight.Normal, FontStyle.Italic),
    )

/** DESIGN.md §3. Uppercase styles (sectionHeader, dateOverline, overline): uppercase the text, not the style. */
@Immutable
data class KavitaTypography(
    val largeTitle: TextStyle = inter(34, FontWeight.ExtraBold, letterSpacingEm = -0.02),
    val seriesTitle: TextStyle = inter(26, FontWeight.ExtraBold, letterSpacingEm = -0.02),
    val title2: TextStyle = inter(22, FontWeight.Bold, letterSpacingEm = -0.01),
    val title3: TextStyle = inter(20, FontWeight.Bold, letterSpacingEm = -0.01),
    val headline: TextStyle = inter(18, FontWeight.Bold),
    val sheetTitle: TextStyle = inter(17, FontWeight.Bold),
    val buttonLarge: TextStyle = inter(17, FontWeight.SemiBold),
    val body: TextStyle = inter(16, FontWeight.Medium),
    val bodyRegular: TextStyle = inter(16, FontWeight.Normal),
    val bodyStrong: TextStyle = inter(16, FontWeight.SemiBold),
    val callout: TextStyle = inter(15, FontWeight.Normal).copy(lineHeight = 21.75.sp),
    val subhead: TextStyle = inter(14, FontWeight.Medium),
    val footnote: TextStyle = inter(13, FontWeight.Normal),
    val sectionHeader: TextStyle = inter(13, FontWeight.Medium, letterSpacingEm = 0.03),
    val dateOverline: TextStyle = inter(13, FontWeight.SemiBold, letterSpacingEm = 0.04),
    val caption: TextStyle = inter(12, FontWeight.Normal),
    val overline: TextStyle = inter(11, FontWeight.Bold, letterSpacingEm = 0.06),
    val tabLabel: TextStyle = inter(11, FontWeight.SemiBold),
    /** Placeholder cover titles: 13–22sp by context, line height 1.1. */
    val coverTitle: TextStyle =
        TextStyle(fontFamily = Literata, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 1.1.em),
    val readerBody: TextStyle =
        TextStyle(fontFamily = Literata, fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 1.7.em),
)

private fun inter(
    sizeSp: Int,
    weight: FontWeight,
    letterSpacingEm: Double = 0.0,
) = TextStyle(fontFamily = Inter, fontSize = sizeSp.sp, fontWeight = weight, letterSpacing = letterSpacingEm.em)
