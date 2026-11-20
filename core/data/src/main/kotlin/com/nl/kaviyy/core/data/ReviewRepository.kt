package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.UpdateUserReviewDto
import com.nl.kaviyy.core.network.apiCall
import retrofit2.HttpException
import javax.inject.Inject

/** A review, as plain text ([body] may still contain HTML from external sites; sanitize before display). */
data class Review(
    val username: String,
    val tagline: String?,
    val body: String,
    /** 0–5 for Kavita users; 0 when absent. */
    val rating: Float,
    /** 0–100 for external reviews; 0 when absent. */
    val score: Int,
    /** Kavita `ScrobbleProvider`: 0 = this server. */
    val provider: Int,
    val siteUrl: String?,
    val mine: Boolean,
)

/** A community score from a Kavita+ provider. */
data class CommunityRating(
    val provider: Int,
    val averageScore: Int,
    val url: String?,
)

data class SeriesReviews(
    val reviews: List<Review> = emptyList(),
    val ratings: List<CommunityRating> = emptyList(),
)

/** Reviews and community ratings (network only; screen 04 sheet). */
interface ReviewRepository {
    suspend fun reviews(
        server: Server,
        seriesId: Int,
        libraryType: Int,
    ): AppResult<SeriesReviews>

    /** Blank [body] deletes the user's review. */
    suspend fun saveReview(
        server: Server,
        seriesId: Int,
        body: String,
    ): AppResult<Unit>
}

private const val NO_CONTENT = 204

class KavitaReviewRepository
    @Inject
    constructor(
        private val apis: ServerApis,
    ) : ReviewRepository {
        override suspend fun reviews(
            server: Server,
            seriesId: Int,
            libraryType: Int,
        ): AppResult<SeriesReviews> =
            apiCall {
                val response = apis.api(server).seriesDetailPlus(seriesId, libraryType)
                if (!response.isSuccessful) throw HttpException(response)
                if (response.code() == NO_CONTENT) null else response.body()
            }.map { dto ->
                SeriesReviews(
                    reviews =
                        dto?.reviews.orEmpty().map { r ->
                            val name = r.username.orEmpty()
                            Review(
                                username = name,
                                tagline = r.tagline?.takeIf { it.isNotBlank() },
                                body = r.bodyJustText ?: r.body.orEmpty(),
                                rating = r.rating,
                                score = r.score,
                                provider = r.provider,
                                siteUrl = r.siteUrl,
                                mine =
                                    !r.isExternal && name.isNotEmpty() &&
                                        name.equals(server.username, ignoreCase = true),
                            )
                        },
                    ratings =
                        dto?.ratings.orEmpty().map {
                            CommunityRating(
                                it.provider,
                                it.averageScore,
                                it.providerUrl,
                            )
                        },
                )
            }

        override suspend fun saveReview(
            server: Server,
            seriesId: Int,
            body: String,
        ): AppResult<Unit> =
            apiCall {
                val api = apis.api(server)
                if (body.isBlank()) {
                    api.deleteSeriesReview(
                        seriesId,
                    )
                } else {
                    api.saveSeriesReview(UpdateUserReviewDto(seriesId, body.trim()))
                }
                Unit
            }
    }
