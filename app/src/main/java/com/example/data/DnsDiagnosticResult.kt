package com.example.data

sealed interface DnsDiagnosticResult {
    object Idle : DnsDiagnosticResult
    object Testing : DnsDiagnosticResult
    data class Success(
        val latencyMs: Long,
        val targetHost: String,
        val resolvedIp: String,
        val adBlockingVerified: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : DnsDiagnosticResult

    data class Failure(
        val targetHost: String,
        val errorMessage: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : DnsDiagnosticResult
}
