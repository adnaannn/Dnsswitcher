package com.example.manager

import android.util.Log
import com.example.data.DnsDiagnosticResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

object DnsDiagnosticRunner {
    private const val TAG = "DnsDiagnosticRunner"

    suspend fun testDnsResolution(targetHost: String = "cloudflare.com"): DnsDiagnosticResult {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.nanoTime()
                val address = InetAddress.getByName(targetHost)
                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

                val hostAddress = address.hostAddress ?: "Unknown IP"
                Log.d(TAG, "DNS resolution of $targetHost succeeded in ${elapsedMs}ms -> $hostAddress")

                DnsDiagnosticResult.Success(
                    latencyMs = elapsedMs,
                    targetHost = targetHost,
                    resolvedIp = hostAddress
                )
            } catch (e: Exception) {
                Log.w(TAG, "DNS resolution failed for $targetHost", e)
                DnsDiagnosticResult.Failure(
                    targetHost = targetHost,
                    errorMessage = e.localizedMessage ?: "DNS lookup timed out or failed."
                )
            }
        }
    }
}
