package com.example.model

data class DnsProfile(
    val id: String,
    val name: String,
    val dohUrl: String,
    val bootstrapIps: List<String> = emptyList(),
    val protocol: String = "DOH",
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false
) {
    val hostname: String
        get() {
            return try {
                val uri = java.net.URI(dohUrl)
                uri.host ?: dohUrl
            } catch (e: Exception) {
                dohUrl.removePrefix("https://").substringBefore("/")
            }
        }

    companion object {
        val ADGUARD = DnsProfile(
            id = "adguard",
            name = "AdGuard DNS",
            dohUrl = "https://dns.adguard-dns.com/dns-query",
            bootstrapIps = listOf("94.140.14.14", "94.140.15.15"),
            protocol = "DOH",
            isEnabled = true,
            isBuiltIn = true
        )

        val CLOUDFLARE = DnsProfile(
            id = "cloudflare",
            name = "Cloudflare DNS",
            dohUrl = "https://cloudflare-dns.com/dns-query",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1"),
            protocol = "DOH",
            isEnabled = true,
            isBuiltIn = true
        )

        val QUAD9 = DnsProfile(
            id = "quad9",
            name = "Quad9 DNS",
            dohUrl = "https://dns.quad9.net/dns-query",
            bootstrapIps = listOf("9.9.9.9", "149.112.112.112"),
            protocol = "DOH",
            isEnabled = true,
            isBuiltIn = true
        )

        val BUILT_IN_PROFILES = listOf(ADGUARD, CLOUDFLARE, QUAD9)
    }
}
