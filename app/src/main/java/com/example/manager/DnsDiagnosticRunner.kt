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

    val RESILIENT_DOMAINS = listOf(
        "google.com",
        "one.one.one.one",
        "dns.google",
        "cloudflare.com",
        "android.com"
    )

    /**
     * Universal DNS connectivity test that resolves common domains ('google.com', 'one.one.one.one', etc.)
     * using the active network's DNS resolver and InetAddress. Completely provider-agnostic,
     * ensuring accurate results across Automatic, AdGuard, Cloudflare, Quad9, or Off.
     *
     * Includes progressive backoff to absorb Private DNS (DoT) and VPN TLS handshake
     * renegotiation windows when switching providers on Wi-Fi and Mobile Data.
     */
    suspend fun testDnsResolution(
        targetDomain: String = PRIMARY_DOMAIN,
        fallbackDomain: String? = if (targetDomain == PRIMARY_DOMAIN) FALLBACK_DOMAIN else null,
        maxRetries: Int = 4,
        retryDelayMs: Long = 400,
        context: android.content.Context? = null
    ): DnsDiagnosticResult {
        return withContext(Dispatchers.IO) {
            // Build the candidate domains list:
            // If caller specified a custom targetDomain (e.g. In unit tests), use targetDomain (+ fallbackDomain)
            // If using default PRIMARY_DOMAIN, test across the resilient domains pool
            val domainsToTest = if (targetDomain == PRIMARY_DOMAIN) {
                RESILIENT_DOMAINS
            } else if (fallbackDomain != null && fallbackDomain != targetDomain) {
                listOf(targetDomain, fallbackDomain)
            } else {
                listOf(targetDomain)
            }

            val cm = context?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            var lastException: Exception? = null
            var testedHost = targetDomain

            for (attempt in 1..maxRetries) {
                for (domain in domainsToTest) {
                    try {
                        testedHost = domain
                        val startTime = System.nanoTime()

                        // Prefer querying through the active network's DNS resolver if available,
                        // otherwise use standard InetAddress.getAllByName()
                        val activeNet = cm?.activeNetwork
                        val addresses = if (activeNet != null) {
                            try {
                                activeNet.getAllByName(domain)
                            } catch (e: Exception) {
                                InetAddress.getAllByName(domain)
                            }
                        } else {
                            InetAddress.getAllByName(domain)
                        }

                        if (addresses.isNotEmpty()) {
                            val elapsedMs = ((System.nanoTime() - startTime) / 1_000_000).coerceAtLeast(1)
                            val resolvedIp = addresses.first().hostAddress ?: "Resolved"
                            Log.d(TAG, "DNS resolution succeeded for $domain on attempt $attempt -> $resolvedIp (${elapsedMs}ms)")

                            val adBlocked = checkAdBlockingQuietly()

                            return@withContext DnsDiagnosticResult.Success(
                                latencyMs = elapsedMs,
                                targetHost = domain,
                                resolvedIp = resolvedIp,
                                adBlockingVerified = adBlocked
                            )
                        }
                    } catch (e: Exception) {
                        lastException = e
                        Log.d(TAG, "DNS resolution attempt $attempt on $domain failed: ${e.message}")
                    }
                }

                if (attempt < maxRetries) {
                    // Backoff progressive delay: 400ms, 800ms, 1200ms to allow DoT / VPN renegotiation
                    val backoff = retryDelayMs * attempt
                    kotlinx.coroutines.delay(backoff)
                }
            }

            // If we reached here, all attempts failed. Build helpful human-facing diagnostics
            val hasActiveNetwork = cm?.activeNetwork != null
            val isNetworkValidated = if (cm != null && cm.activeNetwork != null) {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            } else {
                false
            }

            val humanMessage = when {
                !hasActiveNetwork && context != null ->
                    "No active network connection. Please connect to Wi-Fi or Mobile Data."

                !isNetworkValidated && context != null && lastException is java.net.UnknownHostException ->
                    "DNS TLS handshake in progress. If Private DNS or VPN was just switched, please tap Test again in a few seconds."

                lastException is java.net.UnknownHostException ->
                    "Unable to resolve $targetDomain. If Private DNS or VPN was just switched, allow a moment for network renegotiation."

                lastException != null ->
                    lastException.localizedMessage ?: "DNS resolution failed: ${lastException.javaClass.simpleName}"

                else ->
                    "DNS query timed out without response."
            }

            DnsDiagnosticResult.Failure(
                targetHost = testedHost,
                errorMessage = humanMessage
            )
        }
    }

    private fun checkAdBlockingQuietly(): Boolean {
        return try {
            val addresses = InetAddress.getAllByName("adservice.google.com")
            addresses.any { addr ->
                val ip = addr.hostAddress ?: ""
                ip == "0.0.0.0" || ip == "127.0.0.1" || ip == "::" || ip == "::1"
            }
        } catch (e: java.net.UnknownHostException) {
            // NXDOMAIN or refused query for adservice is standard sinkhole behavior of ad-blockers
            true
        } catch (e: Exception) {
            false
        }
    }
}
