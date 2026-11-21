package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.SeriesDao
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.network.apiCall
import com.nl.kaviyy.core.network.kavitaTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Want to Read series that gained chapters since the app last saw them. The cached series rows are the watermark:
 * a series seen for the first time is only recorded, never reported.
 */
@Singleton
class NewChapterCheck
    @Inject
    constructor(
        private val apis: ServerApis,
        private val series: SeriesDao,
    ) {
        // ponytail: first page only (100 series); a longer Want to Read list needs paging here.
        suspend fun check(server: Server): AppResult<List<String>> {
            val id = server.id.value
            val remote =
                when (val r = apiCall { apis.api(server).wantToRead(SeriesFilterV2Dto(), 1, PAGE_SIZE) }) {
                    is AppResult.Failure -> return r
                    is AppResult.Success -> r.value
                }
            val seen = series.seriesRows(id, remote.map { it.id }).associateBy { it.id }
            val fresh =
                remote.filter { dto ->
                    val last = seen[dto.id]?.lastChapterAdded ?: 0
                    last > 0 && kavitaTime(dto.lastChapterAddedUtc) > last
                }
            series.upsertSeries(remote.map { it.toEntity(id) })
            return AppResult.Success(fresh.map { it.localizedName?.takeIf(String::isNotBlank) ?: it.name.orEmpty() })
        }

        private companion object {
            const val PAGE_SIZE = 100
        }
    }
