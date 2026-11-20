package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

/** Home hero card (radius 22, padding 16): cover 92×138, overline, title, progress, Resume. */
@Composable
fun ContinueReadingCard(
    overline: String,
    title: String,
    subtitle: String,
    coverColor: Color,
    progress: Float,
    remaining: String,
    resumeLabel: String,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    coverOverline: String? = null,
) {
    val colors = Kavita.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .cssShadow(KavitaShapes.heroCard, 1.dp, 2.dp, 0.04f)
                .background(colors.surface, KavitaShapes.heroCard)
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoverImage(
            title = title,
            color = coverColor,
            overline = coverOverline,
            titleSize = 15.sp,
            titleBottomInset = 10.dp,
            sideInset = 9.dp,
            modifier = Modifier.size(92.dp, 138.dp).cssShadow(KavitaShapes.coverGrid, 6.dp, 16.dp, 0.30f, coverColor),
        )
        // Grows with large font scales instead of clipping; the cover sets the minimum height.
        Column(
            Modifier.weight(1f).fillMaxHeight().heightIn(min = 138.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(overline.uppercase(), style = Kavita.type.overline, color = colors.accent)
                Text(
                    title,
                    style = Kavita.type.title3,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = Kavita.type.subhead.copy(fontWeight = FontWeight.Normal),
                    color = colors.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressBar(progress, height = 6.dp)
                Text(remaining, style = Kavita.type.caption, color = colors.labelSecondary, maxLines = 1)
            }
            PrimaryButton(resumeLabel, onResume, size = ButtonSize.Small, leadingIcon = KavitaIcons.Play)
        }
    }
}
