package com.example.data

sealed interface DnsSwitchResult {
    data class Success(
        val provider: DnsProvider,
        val verifiedHostname: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : DnsSwitchResult

    data class PermissionDenied(
        val requiredCommand: String,
        val reason: String = "Missing WRITE_SECURE_SETTINGS permission."
    ) : DnsSwitchResult

    data class VerificationFailed(
        val expectedProvider: DnsProvider,
        val actualMode: String?,
        val actualSpecifier: String?
    ) : DnsSwitchResult

    data class Error(
        val message: String
    ) : DnsSwitchResult
}
