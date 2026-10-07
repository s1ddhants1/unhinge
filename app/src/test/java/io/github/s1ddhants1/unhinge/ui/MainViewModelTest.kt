package io.github.s1ddhants1.unhinge.ui

import android.app.Application
import io.github.s1ddhants1.unhinge.domain.repository.HingeDataRepository
import io.github.s1ddhants1.unhinge.domain.usecase.GetHingeDataUseCase
import io.github.s1ddhants1.unhinge.model.CompleteHingeData
import io.github.s1ddhants1.unhinge.util.LSPatchHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MainViewModelTest {

    private class FakeHingeDataRepository(
        private val data: CompleteHingeData
    ) : HingeDataRepository {
        override suspend fun getCompleteHingeData(): CompleteHingeData = data
    }

    @Test
    fun updateFrameworkEvaluation_preservesRootStatus() = runBlocking {
        val app = Application()
        val viewModel = MainViewModel(
            application = app,
            getHingeDataUseCase = GetHingeDataUseCase(FakeHingeDataRepository(CompleteHingeData(isRootGranted = true))),
            rootChecker = { true },
            customScope = this
        )

        val eval = LSPatchHelper.BannerEvaluation(
            isConnected = true,
            isInjectable = true,
            frameworkName = "LSPosed",
            frameworkVersion = "1.9.2",
            title = "LSPosed Active",
            desc = "Connected to LSPosed 1.9.2 (API 100)"
        )

        viewModel.updateFrameworkEvaluation(eval)

        val state = viewModel.uiState.value
        assertEquals("LSPosed Active", state.bannerTitle)
        assertEquals("Connected to LSPosed 1.9.2 (API 100)", state.bannerDesc)
        assertTrue(state.isFrameworkConnected)
        assertTrue(state.isInjectable)
    }

    @Test
    fun loadInsights_updatesRootStatusAndData() = runBlocking {
        val app = Application()
        val expectedData = CompleteHingeData(isRootGranted = true)
        val viewModel = MainViewModel(
            application = app,
            getHingeDataUseCase = GetHingeDataUseCase(FakeHingeDataRepository(expectedData)),
            rootChecker = { false },
            customScope = this
        )

        viewModel.loadInsights()?.join()

        val state = viewModel.uiState.value
        assertTrue(state.completeData.isRootGranted)
        assertEquals(true, state.isRootGranted)
    }

    @Test
    fun checkRootStatus_updatesRootGranted() = runBlocking {
        val app = Application()
        val viewModel = MainViewModel(
            application = app,
            getHingeDataUseCase = GetHingeDataUseCase(FakeHingeDataRepository(CompleteHingeData())),
            rootChecker = { true },
            customScope = this
        )

        viewModel.checkRootStatus().join()

        val state = viewModel.uiState.value
        assertEquals(true, state.isRootGranted)
    }
}
