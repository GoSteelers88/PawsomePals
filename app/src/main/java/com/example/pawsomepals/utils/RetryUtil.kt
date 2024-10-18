package com.example.pawsomepals.utils

import kotlinx.coroutines.delay
import kotlin.math.pow
import android.util.Log

suspend fun <T> retryWithExponentialBackoff(
    times: Int = 3,
    initialDelay: Long = 100,
    maxDelay: Long = 1000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            Log.e("RetryUtil", "Attempt ${it + 1} failed", e)
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block() // last attempt
}