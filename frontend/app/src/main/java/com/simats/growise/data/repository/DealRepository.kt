package com.simats.growise.data.repository

import com.simats.growise.data.model.TransitionDealRequest
import com.simats.growise.data.network.RetrofitClient

class DealRepository {
    suspend fun transitionDeal(request: TransitionDealRequest): retrofit2.Response<com.simats.growise.data.model.StandardBackendResponse> {
        return RetrofitClient.apiService.transitionDeal(request)
    }
}
