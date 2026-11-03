package io.github.s1ddhants1.unhinge.domain.usecase

import io.github.s1ddhants1.unhinge.domain.repository.HingeDataRepository
import io.github.s1ddhants1.unhinge.model.CompleteHingeData
import io.github.s1ddhants1.unhinge.model.RawHingeTelemetry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetHingeDataUseCaseTest {

    private class FakeHingeDataRepository(
        private val data: CompleteHingeData
    ) : HingeDataRepository {
        override suspend fun getCompleteHingeData(): CompleteHingeData = data
    }

    @Test
    fun invoke_returnsCompleteDataFromRepository() = runBlocking {
        val expectedData = CompleteHingeData(
            telemetry = RawHingeTelemetry(firstName = "Alex", metroArea = "San Francisco"),
            isRootGranted = true
        )
        val fakeRepo = FakeHingeDataRepository(expectedData)
        val useCase = GetHingeDataUseCase(fakeRepo)

        val result = useCase()

        assertEquals("Alex", result.telemetry.firstName)
        assertEquals("San Francisco", result.telemetry.metroArea)
        assertTrue(result.isRootGranted)
    }
}
