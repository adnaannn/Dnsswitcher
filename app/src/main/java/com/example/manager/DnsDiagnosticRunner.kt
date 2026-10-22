package com.example.manager

import android.util.Log
import com.example.data.DnsDiagnosticResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

object DnsDiagnosticRunner {
    private const val TAG = "DnsDiagnosticRunner"

    suspend fun testDnsResolution(
        testHosts: List<String> = listOf("google.com", "cloudflare.com", "wikipedia.org")
    ): DnsDiagnosticResult {
        return withContext(Dispatchers.IO) {
            var lastError: Exception? = null

            for (host in testHosts) {
                try {
                    val startTime = System.nanoTime()
                    val address = InetAddress.getByName(host)
                    val elapsedMs = ((System.nanoTime() - startTime) / 1_000_000).coerceAtLeast(1)

                    val hostAddress = address.hostAddress ?: "Resolved"
                    Log.d(TAG, "DNS resolution of $host succeeded in ${elapsedMs}ms -> $hostAddress")

                    // Test ad-blocking effectiveness on known ad tracking domain
                    var isAdBlocked = false
                    try {
                        val adHost = InetAddress.getByName("adservice.google.com")
                        val adIp = adHost.hostAddress
                        if (adIp == "0.0.0.0" || adIp == "127.0.0.1" || adIp == null) {
                            isAdBlocked = true
                        }
                    } catch (e: Exception) {
                        // When AdGuard blocks the domain, it throws UnknownHostException
                        isAdBlocked = true
                    }

                    return@withContext DnsDiagnosticResult.Success(
                        latencyMs = elapsedMs,
                        targetHost = host,
                        resolvedIp = hostAddress,
                        adBlockingVerified = isAdBlocked
                    )
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "DNS resolution failed for $host, trying fallback host...", e)
                }
            }

            DnsDiagnosticResult.Failure(
                targetHost = testHosts.firstOrNull() ?: "google.com",
                errorMessage = lastError?.localizedMessage ?: "Unable to resolve host: DNS lookup failed or network is unreachable."
            )
        }
    }
}
