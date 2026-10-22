package com.example.manager

import android.util.Log
import com.example.data.DnsDiagnosticResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

object DnsDiagnosticRunner {
    private const val TAG = "DnsDiagnosticRunner"
    const val PRIMARY_DOMAIN = "google.com"
    const val FALLBACK_DOMAIN = "cloudflare.com"

    /**
     * Universal DNS connectivity test that resolves a common domain ('google.com')
     * using standard InetAddress.getByName(). Completely provider-agnostic,
     * ensuring accurate results across Automatic, AdGuard, Cloudflare, Quad9, or Off.
     */
    suspend fun testDnsResolution(
        targetDomain: String = PRIMARY_DOMAIN,
        fallbackDomain: String? = if (targetDomain == PRIMARY_DOMAIN) FALLBACK_DOMAIN else null,
        maxRetries: Int = 3,
        retryDelayMs: Long = 350
    ): DnsDiagnosticResult {
        return withContext(Dispatchers.IO) {
            val domainsToTest = if (fallbackDomain != null && fallbackDomain != targetDomain) {
                listOf(targetDomain, fallbackDomain)
            } else {
                listOf(targetDomain)
            }
            var lastException: Exception? = null

            for (attempt in 1..maxRetries) {
                for (domain in domainsToTest) {
                    try {
                        val startTime = System.nanoTime()
                        val inetAddress = InetAddress.getByName(domain)
                        val elapsedMs = ((System.nanoTime() - startTime) / 1_000_000).coerceAtLeast(1)

                        val resolvedIp = inetAddress.hostAddress ?: "Resolved"
                        Log.d(TAG, "DNS resolution succeeded for $domain on attempt $attempt -> $resolvedIp (${elapsedMs}ms)")

                        val adBlocked = checkAdBlockingQuietly()

                        return@withContext DnsDiagnosticResult.Success(
                            latencyMs = elapsedMs,
                            targetHost = domain,
                            resolvedIp = resolvedIp,
                            adBlockingVerified = adBlocked
                        )
                    } catch (e: Exception) {
                        lastException = e
                        Log.w(TAG, "DNS resolution failed for $domain on attempt $attempt: ${e.message}")
                    }
                }

                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(retryDelayMs)
                }
            }

            val humanMessage = when (lastException) {
                is java.net.UnknownHostException -> "Unable to resolve $targetDomain. If Private DNS or VPN was just switched, allow a moment for network renegotiation."
                null -> "DNS query timed out without response."
                else -> lastException.localizedMessage ?: "DNS resolution failed: ${lastException.javaClass.simpleName}"
            }

            DnsDiagnosticResult.Failure(
                targetHost = targetDomain,
                errorMessage = humanMessage
            )
        }
    }

    private fun checkAdBlockingQuietly(): Boolean {
        return try {
            val adHost = InetAddress.getByName("adservice.google.com")
            val ip = adHost.hostAddress
            ip == "0.0.0.0" || ip == "127.0.0.1" || ip == null
        } catch (e: Exception) {
            true
        }
    }
}
