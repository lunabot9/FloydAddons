package com.odtheking.odin.utils.network

import com.odtheking.odin.FloydAddonsMod.logger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.coroutines.resume

object WebUtils {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    suspend fun getInputStream(url: String): Result<InputStream> =
        executeRequest(createGetRequest(url))
            .map { response -> response.body().byteInputStream() }
            .onFailure { logger.warn("Failed to get input stream from $url: ${it.message}") }

    private fun createGetRequest(url: String): HttpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build()

    private suspend fun executeRequest(request: HttpRequest): Result<HttpResponse<String>> = suspendCancellableCoroutine { cont ->
        logger.info("Making request to ${request.uri()}")

        val future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())

        cont.invokeOnCancellation {
            logger.info("Cancelling request to ${request.uri()}")
            future.cancel(true)
        }

        future.whenComplete { response, error ->
            if (error != null) {
                if (cont.isActive) {
                    logger.warn("Request failed for ${request.uri()}: ${error.message}")
                    cont.resume(Result.failure(error))
                }
            } else {
                if (!cont.isActive) return@whenComplete

                if (response.statusCode() in 200..299) cont.resume(Result.success(response))
                else cont.resume(Result.failure(InputStreamException(response.statusCode(), request.uri().toString())))
            }
        }
    }

    class InputStreamException(code: Int, url: String) : Exception("Failed to get input stream from $url: HTTP $code")
}
