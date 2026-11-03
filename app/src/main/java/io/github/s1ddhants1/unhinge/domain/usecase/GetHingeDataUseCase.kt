package io.github.s1ddhants1.unhinge.domain.usecase

import io.github.s1ddhants1.unhinge.domain.repository.HingeDataRepository
import io.github.s1ddhants1.unhinge.model.CompleteHingeData

class GetHingeDataUseCase(
    private val repository: HingeDataRepository
) {
    suspend operator fun invoke(): CompleteHingeData {
        return repository.getCompleteHingeData()
    }
}
