package io.github.s1ddhants1.unhinge.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.data.SuStorageReader
import io.github.s1ddhants1.unhinge.data.repository.HingeDataRepositoryImpl
import io.github.s1ddhants1.unhinge.domain.usecase.GetHingeDataUseCase
import io.github.s1ddhants1.unhinge.model.*
import io.github.s1ddhants1.unhinge.util.LSPatchHelper
import kotlinx.coroutines.Dispatchers
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
    val isRootGranted: Boolean? = null,
    val completeData: CompleteHingeData = CompleteHingeData()
)

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val getHingeDataUseCase: GetHingeDataUseCase = GetHingeDataUseCase(HingeDataRepositoryImpl(application)),
    private val rootChecker: () -> Boolean = { SuStorageReader.checkRoot() },
    private val customScope: kotlinx.coroutines.CoroutineScope? = null
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private val scope: kotlinx.coroutines.CoroutineScope
        get() = customScope ?: viewModelScope

    init {
        checkRootStatus()
    }

    fun checkRootStatus(): kotlinx.coroutines.Job {
        return scope.launch(Dispatchers.IO) {
            val isRoot = rootChecker()
            _uiState.value = _uiState.value.copy(isRootGranted = isRoot)
        }
    }

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

    fun loadInsights(): kotlinx.coroutines.Job? {
        if (_uiState.value.isRefreshing) {
            Log.d(Consts.TAG, "MainViewModel: loadInsights already in progress, skipping")
            return null
        }
        return scope.launch {
            Log.d(Consts.TAG, "MainViewModel: loadInsights started via GetHingeDataUseCase")
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val fullData = getHingeDataUseCase()
                Log.d(Consts.TAG, "MainViewModel: GetHingeDataUseCase finished, root=${fullData.isRootGranted}, name=${fullData.telemetry.firstName}, tables=${fullData.databaseTables.size}")
                _uiState.value = _uiState.value.copy(
                    completeData = fullData,
                    isRootGranted = fullData.isRootGranted,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                Log.e(Consts.TAG, "MainViewModel: Error loading insights", e)
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }
}
