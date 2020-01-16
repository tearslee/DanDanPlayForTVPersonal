package com.seiko.core.data.api

import android.util.Log
import com.seiko.core.data.db.model.BangumiDetailsEntity
import com.seiko.core.data.db.model.BangumiIntroEntity
import com.seiko.core.model.api.BangumiSeason
import com.seiko.core.data.Result
import com.seiko.core.model.api.SearchAnimeDetails
import com.seiko.core.util.safeApiCall
import okio.IOException

internal class DanDanApiRemoteDataSource(private val api: DanDanApiService) {

    suspend fun getBangumiList(): Result<List<BangumiIntroEntity>> {
        return safeApiCall(
            call = { requestBangumiList() },
            errorMessage = "Error get BangumiIntroList"
        )
    }

    private suspend fun requestBangumiList(): Result<List<BangumiIntroEntity>> {
        val response = api.getBangumiList()
        if (response.success) {
            return Result.Success(response.bangumiList)
        }
        return Result.Error(IOException("Error get BangumiIntroList " +
                "${response.errorCode} ${response.errorMessage}"))
    }

    suspend fun getBangumiSeasons(): Result<List<BangumiSeason>> {
        return safeApiCall(
            call = { requestBangumiSeasons() },
            errorMessage = "Error get BangumiSeasonList"
        )
    }

    private suspend fun requestBangumiSeasons(): Result<List<BangumiSeason>> {
        val response = api.getBangumiSeasons()
        if (response.success) {
            return Result.Success(response.seasons)
        }
        return Result.Error(IOException("Error get BangumiSeasonList " +
                "${response.errorCode} ${response.errorMessage}"))
    }

    suspend fun getBangumiListWithSeason(season: BangumiSeason): Result<List<BangumiIntroEntity>> {
        return safeApiCall(
            call = { requestBangumiListWithSeason(season) },
            errorMessage = "Error get BangumiSeasonList"
        )
    }

    private suspend fun requestBangumiListWithSeason(season: BangumiSeason): Result<List<BangumiIntroEntity>> {
        val response = api.getBangumiListWithSeason(season.year, season.month)
        if (response.success) {
            return Result.Success(response.bangumiList)
        }
        return Result.Error(IOException("Error get BangumiIntroList " +
                "${response.errorCode} ${response.errorMessage}"))
    }

    suspend fun getBangumiDetails(animeId: Long): Result<BangumiDetailsEntity> {
        return safeApiCall(
            call = { requestBangumiDetails(animeId) },
            errorMessage = "Error get BangumiDetailsList"
        )
    }

    private suspend fun requestBangumiDetails(animeId: Long): Result<BangumiDetailsEntity> {
        val response = api.getBangumiDetails(animeId)
        if (response.success) {
            return Result.Success(response.bangumi)
        }
        return Result.Error(IOException("Error get BangumiDetailsList " +
                "${response.errorCode} ${response.errorMessage}"))
    }

    suspend fun searchBangumiList(keyword: String, type: String): Result<List<SearchAnimeDetails>> {
        return safeApiCall(
            call = { requestSearchBangumiList(keyword, type) },
            errorMessage = "Error Search BangumiList"
        )
    }

    private suspend fun requestSearchBangumiList(keyword: String, type: String): Result<List<SearchAnimeDetails>> {
        val response = api.searchBangumiList(keyword, type)
        if (response.success) {
            return Result.Success(response.animes)
        }
        return Result.Error(IOException("Error Search BangumiList " +
                "${response.errorCode} ${response.errorMessage}"))
    }
}