package io.github.s1ddhants1.unhinge.domain.repository

import io.github.s1ddhants1.unhinge.model.CompleteHingeData

interface HingeDataRepository {
    suspend fun getCompleteHingeData(): CompleteHingeData
}
