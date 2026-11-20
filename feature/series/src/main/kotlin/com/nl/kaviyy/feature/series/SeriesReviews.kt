package com.nl.kaviyy.feature.series

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nl.kaviyy.core.data.CommunityRating
import com.nl.kaviyy.core.data.Review
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import kotlin.math.roundToInt

private const val STARS = 5

/** [derived] Screen 04 has no reviews layout: built from SectionHeader + GroupedList like the other sheets. */
@Composable
internal fun ReviewsContent(
    state: SeriesUiState,
    reviewText: TextFieldState,
    actions: SeriesActions,
) {
    val rating = state.detail?.series?.userRating ?: 0f
    SectionHeader(stringResource(R.string.series_your_rating))
    Stars(rating.roundToInt(), actions.onRate)
    SectionHeader(stringResource(R.string.series_your_review), Modifier.padding(top = 8.dp))
    GroupedList {
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(16.dp)) {
            if (reviewText.text.isEmpty()) {
                Text(
                    stringResource(R.string.series_review_hint),
                    style = Kavita.type.bodyRegular,
                    color = Kavita.colors.placeholder,
                )
            }
            val label = stringResource(R.string.series_your_review)
            BasicTextField(
                reviewText,
                Modifier.fillMaxWidth().semantics { contentDescription = label },
                textStyle = Kavita.type.bodyRegular.copy(color = Kavita.colors.label),
                cursorBrush = SolidColor(Kavita.colors.accent),
            )
        }
    }
    state.reviewError?.let {
        Text(
            stringResource(R.string.series_review_failed),
            style = Kavita.type.footnote,
            color = Kavita.colors.destructive,
        )
    }
    SecondaryButton(stringResource(R.string.series_review_save), actions.onSaveReview, Modifier.fillMaxWidth())
    when (val r = state.reviews) {
        ReviewsState.Loading -> {
            Hint(stringResource(R.string.series_loading))
        }

        is ReviewsState.Failed -> {
            Hint(stringResource(R.string.series_reviews_failed))
        }

        is ReviewsState.Loaded -> {
            CommunityRatings(r.value.ratings)
            OtherReviews(r.value.reviews.filterNot { it.mine })
        }
    }
    Links(
        state.detail
            ?.metadata
            ?.webLinks
            .orEmpty(),
    )
}

/** Five 48dp stars; tapping the current rating clears it. */
@Composable
private fun Stars(
    rating: Int,
    onRate: (Float) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (star in 1..STARS) {
            val on = star <= rating
            IconButton(onClick = { onRate(if (star == rating) 0f else star.toFloat()) }) {
                Icon(
                    KavitaIcons.Star,
                    stringResource(R.string.series_rate_stars, star),
                    Modifier.size(28.dp),
                    tint = if (on) Kavita.colors.rating else Kavita.colors.fillSegmented,
                )
            }
        }
    }
}

@Composable
private fun CommunityRatings(ratings: List<CommunityRating>) {
    if (ratings.isEmpty()) return
    SectionHeader(stringResource(R.string.series_community), Modifier.padding(top = 8.dp))
    GroupedList {
        ratings.forEachIndexed { i, r ->
            GroupedRow(
                stringResource(providerLabel(r.provider)),
                value = stringResource(R.string.series_state_percent, r.averageScore),
                divider = i < ratings.lastIndex,
            )
        }
    }
}

@Composable
private fun OtherReviews(reviews: List<Review>) {
    if (reviews.isEmpty()) return
    SectionHeader(stringResource(R.string.series_reviews), Modifier.padding(top = 8.dp))
    val context = LocalContext.current
    reviews.forEach { r ->
        Column(
            Modifier.fillMaxWidth().background(Kavita.colors.surface, KavitaShapes.groupedList).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    r.username,
                    style = Kavita.type.subhead,
                    color = Kavita.colors.label,
                    modifier = Modifier.weight(1f),
                )
                reviewScore(r)?.let { Text(it, style = Kavita.type.footnote, color = Kavita.colors.labelSecondary) }
            }
            r.tagline?.let { Text(plainSummary(it), style = Kavita.type.bodyStrong, color = Kavita.colors.label) }
            Text(
                plainSummary(r.body),
                style = Kavita.type.callout,
                color = Kavita.colors.labelBody,
                maxLines = REVIEW_LINES,
            )
            webUrl(r.siteUrl)?.let { url ->
                Text(
                    stringResource(R.string.series_review_open),
                    style = Kavita.type.footnote,
                    color = Kavita.colors.accent,
                    modifier = Modifier.padding(top = 4.dp).clickableOpen(context, url),
                )
            }
        }
    }
}

@Composable
private fun Links(links: List<String>) {
    val urls = links.mapNotNull(::webUrl)
    if (urls.isEmpty()) return
    val context = LocalContext.current
    SectionHeader(stringResource(R.string.series_links), Modifier.padding(top = 8.dp))
    GroupedList {
        urls.forEachIndexed { i, url ->
            GroupedRow(url.toUri().host ?: url, onClick = { open(context, url) }, divider = i < urls.lastIndex)
        }
    }
}

private const val REVIEW_LINES = 8

@Composable
private fun reviewScore(r: Review): String? =
    when {
        r.rating > 0f -> stringResource(R.string.series_rating, r.rating)
        r.score > 0 -> stringResource(R.string.series_state_percent, r.score)
        else -> null
    }

/** Only http(s) links leave the app (no intent:, javascript:, file: …). */
internal fun webUrl(raw: String?): String? {
    val url = raw?.trim().orEmpty()
    val scheme = url.substringBefore(':', "").lowercase()
    return url.takeIf { scheme == "https" || scheme == "http" }
}

private fun open(
    context: Context,
    url: String,
) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    runCatching { context.startActivity(intent) }
}

private fun Modifier.clickableOpen(
    context: Context,
    url: String,
): Modifier = clickable(role = Role.Button) { open(context, url) }

/** Indexed by Kavita `ScrobbleProvider` (3 is unused). */
private val PROVIDER_LABELS =
    listOf(
        R.string.provider_kavita,
        R.string.provider_anilist,
        R.string.provider_mal,
        R.string.provider_kavita,
        R.string.provider_cbr,
        R.string.provider_hardcover,
    )

private fun providerLabel(provider: Int): Int = PROVIDER_LABELS.getOrElse(provider) { R.string.provider_kavita }
