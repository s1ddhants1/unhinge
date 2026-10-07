package io.github.s1ddhants1.unhinge.ai

import android.util.Log

object Timber {
    private const val TAG = "UnhingeAI"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun e(message: String) {
        Log.e(TAG, message)
    }

    fun e(throwable: Throwable, message: String) {
        Log.e(TAG, message, throwable)
    }
}
