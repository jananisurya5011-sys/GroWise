package com.simats.growise.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simats.growise.data.model.TransitionDealRequest
import com.simats.growise.data.repository.DealRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DealViewModel : ViewModel() {
    private val repository = DealRepository()

    private val _transitionState = MutableStateFlow<DealTransitionState>(DealTransitionState.Idle)
    val transitionState: StateFlow<DealTransitionState> = _transitionState

    suspend fun transitionDeal(request: TransitionDealRequest): retrofit2.Response<com.simats.growise.data.model.StandardBackendResponse> {
        _transitionState.value = DealTransitionState.Loading
        val response = repository.transitionDeal(request)
        if (response.isSuccessful) {
            _transitionState.value = DealTransitionState.Success
        } else {
            _transitionState.value = DealTransitionState.Error("Network Error or Invalid Transition")
        }
        return response
    }

    fun resetState() {
        _transitionState.value = DealTransitionState.Idle
    }
}

sealed class DealTransitionState {
    object Idle : DealTransitionState()
    object Loading : DealTransitionState()
    object Success : DealTransitionState()
    data class Error(val message: String) : DealTransitionState()
}
