package com.kanyandula.discovernearby.places

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The body of a successful response, or null for an HTTP error. The body is read on OkHttp's thread, so the caller
 * (Main, in the app) never blocks on it, and cancelling the coroutine cancels the call, body read included (stale
 * requests, docs/03 §15). A failed read surfaces as an [IOException].
 */
internal suspend fun Call.awaitBody(): String? = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            runCatching { response.use { if (it.isSuccessful) it.body.string() else null } }
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it as? IOException ?: IOException("Body read failed")) }
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
}
