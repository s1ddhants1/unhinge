package io.github.s1ddhants1.unhinge.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.data.repository.HingeDataRepositoryImpl
import io.github.s1ddhants1.unhinge.domain.usecase.GetHingeDataUseCase
import io.github.s1ddhants1.unhinge.model.*
import io.github.s1ddhants1.unhinge.util.LSPatchHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val isFrameworkConnected: Boolean = false,
    val isInjectable: Boolean = false,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val bannerTitle: String = "Checking framework status...",
    val bannerDesc: String = "",
    val isIntegrated: Boolean = false,
    val isRefreshing: Boolean = false,
    val completeData: CompleteHingeData = CompleteHingeData()
)

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val getHingeDataUseCase: GetHingeDataUseCase = GetHingeDataUseCase(HingeDataRepositoryImpl(application))
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    fun updateFrameworkEvaluation(eval: LSPatchHelper.BannerEvaluation) {
        _uiState.value = _uiState.value.copy(
            isFrameworkConnected = eval.isConnected,
            isInjectable = eval.isInjectable,
            frameworkName = eval.frameworkName,
            frameworkVersion = eval.frameworkVersion,
            bannerTitle = eval.title,
            bannerDesc = eval.desc,
            isIntegrated = eval.isIntegrated
        )
    }

    fun loadInsights() {
        if (_uiState.value.isRefreshing) {
            Log.d(Consts.TAG, "MainViewModel: loadInsights already in progress, skipping")
            return
        }
        viewModelScope.launch {
            Log.d(Consts.TAG, "MainViewModel: loadInsights started via GetHingeDataUseCase")
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val fullData = getHingeDataUseCase()
            Log.d(Consts.TAG, "MainViewModel: GetHingeDataUseCase finished, root=${fullData.isRootGranted}, name=${fullData.telemetry.firstName}, tables=${fullData.databaseTables.size}")
            _uiState.value = _uiState.value.copy(completeData = fullData, isRefreshing = false)
        }
    }
}
