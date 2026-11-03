package io.github.s1ddhants1.unhinge.data.repository

import android.content.Context
import io.github.s1ddhants1.unhinge.data.SuStorageReader
import io.github.s1ddhants1.unhinge.domain.repository.HingeDataRepository
import io.github.s1ddhants1.unhinge.model.CompleteHingeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HingeDataRepositoryImpl(
    private val context: Context
) : HingeDataRepository {
    override suspend fun getCompleteHingeData(): CompleteHingeData {
        val appContext = context.applicationContext ?: context
        return withContext(Dispatchers.IO) {
            SuStorageReader.readCompleteData(appContext)
        }
    }
}
