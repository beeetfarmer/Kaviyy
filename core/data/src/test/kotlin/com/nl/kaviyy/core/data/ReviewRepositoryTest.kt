package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.RatingDto
import com.nl.kaviyy.core.network.SeriesDetailPlusDto
import com.nl.kaviyy.core.network.UpdateUserReviewDto
import com.nl.kaviyy.core.network.UserReviewDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response
import kotlin.test.assertEquals

class ReviewRepositoryTest {
    private val server =
        Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url, username = "Mira")
    private var plus: Response<SeriesDetailPlusDto> = Response.success(204, null as SeriesDetailPlusDto?)
    private val calls = mutableListOf<String>()

    private val api =
        object : FakeKavitaApi() {
            override suspend fun seriesDetailPlus(
                seriesId: Int,
                libraryType: Int,
            ) = plus

            override suspend fun saveSeriesReview(body: UpdateUserReviewDto): UserReviewDto {
                calls += "save ${body.body}"
                return UserReviewDto()
            }

            override suspend fun deleteSeriesReview(seriesId: Int) {
                calls += "delete $seriesId"
            }
        }

    private val repo = KavitaReviewRepository { api }

    @Test
    fun noContentMeansNoReviewsAndErrorsAreReported() =
        runTest {
            assertEquals(AppResult.Success(SeriesReviews()), repo.reviews(server, 7, 0))
            plus = Response.error(403, "".toResponseBody())
            assertEquals(AppResult.Failure(AppError.Forbidden), repo.reviews(server, 7, 0))
        }

    @Test
    fun reviewsMapToPlainFieldsAndMineIsTheSignedInUser() =
        runTest {
            plus =
                Response.success(
                    SeriesDetailPlusDto(
                        reviews =
                            listOf(
                                UserReviewDto(username = "mira", bodyJustText = "Loved it", rating = 4.5f),
                                UserReviewDto(
                                    username = "Mira",
                                    body = "<p>ext</p>",
                                    isExternal = true,
                                    score = 80,
                                    provider = 1,
                                ),
                            ),
                        ratings =
                            listOf(
                                RatingDto(averageScore = 82, provider = 1, providerUrl = "https://anilist.co/x"),
                            ),
                    ),
                )
            val r = (repo.reviews(server, 7, 0) as AppResult.Success).value
            assertEquals(listOf(true, false), r.reviews.map { it.mine })
            assertEquals(listOf("Loved it", "<p>ext</p>"), r.reviews.map { it.body })
            assertEquals(listOf(CommunityRating(1, 82, "https://anilist.co/x")), r.ratings)
        }

    @Test
    fun blankReviewDeletes() =
        runTest {
            repo.saveReview(server, 7, "  Great  ")
            repo.saveReview(server, 7, "   ")
            assertEquals(listOf("save Great", "delete 7"), calls)
        }
}
