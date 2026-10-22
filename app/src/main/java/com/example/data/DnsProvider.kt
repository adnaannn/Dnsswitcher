package com.example.data

import androidx.annotation.DrawableRes
import com.example.R

enum class DnsProvider(
    val id: String,
    val displayName: String,
    val hostname: String,
    val mode: String,
    val description: String,
    val badge: String,
    @DrawableRes val iconRes: Int
) {
    OFF(
        id = "off",
        displayName = "DNS Off",
        hostname = "",
        mode = "off",
        description = "Private DNS is disabled. Queries use standard network unencrypted DNS.",
        badge = "Private DNS Off",
        iconRes = R.drawable.ic_dns_off
    ),
    ADGUARD(
        id = "adguard",
        displayName = "AdGuard DNS",
        hostname = "dns.adguard.com",
        mode = "hostname",
        description = "Blocks game ads, video popups, web trackers, and malicious phishing domains on Wi-Fi and Mobile Data.",
        badge = "Ad-Blocking & Privacy",
        iconRes = R.drawable.ic_shield_adguard
    ),
    CLOUDFLARE(
        id = "cloudflare",
        displayName = "Cloudflare DNS",
        hostname = "one.one.one.one",
        mode = "hostname",
        description = "Ultra-fast 1.1.1.1 Private DNS (one.one.one.one) with strict zero-logging privacy.",
        badge = "Fast 1.1.1.1 & Zero-Logging",
        iconRes = R.drawable.ic_cloud_cloudflare
    ),
    QUAD9(
        id = "quad9",
        displayName = "Quad9 DNS",
        hostname = "dns.quad9.net",
        mode = "hostname",
        description = "Blocks malware, spyware, and botnets with threat intelligence from IBM and CERT.",
        badge = "Threat Protection",
        iconRes = R.drawable.ic_shield_quad9
    ),
    AUTOMATIC(
        id = "automatic",
        displayName = "Automatic",
        hostname = "",
        mode = "opportunistic",
        description = "System default opportunistic DNS-over-TLS (DoT). Uses ISP or network DNS.",
        badge = "System Default",
        iconRes = R.drawable.ic_dns_automatic
    );

    companion object {
        fun fromSpecifier(mode: String?, specifier: String?): DnsProvider {
            val safeMode = mode?.trim()?.lowercase() ?: ""
            val safeSpecifier = specifier?.trim()?.lowercase() ?: ""

            return when {
                safeMode == "off" -> OFF
                safeMode == "hostname" -> {
                    when {
                        safeSpecifier.contains("adguard") -> ADGUARD
                        safeSpecifier == "one.one.one.one" ||
                        safeSpecifier == "1.1.1.1" ||
                        safeSpecifier == "1.0.0.1" ||
                        safeSpecifier.contains("cloudflare") -> CLOUDFLARE
                        safeSpecifier.contains("quad9") -> QUAD9
                        safeSpecifier.isEmpty() -> AUTOMATIC
                        else -> {
                            if (safeSpecifier.contains("adguard.com") || safeSpecifier.contains("adguard-dns.com")) ADGUARD
                            else if (safeSpecifier.contains("one.one.one.one") || safeSpecifier.contains("1.1.1.1")) CLOUDFLARE
                            else if (safeSpecifier.contains("quad9.net") || safeSpecifier.contains("9.9.9.9")) QUAD9
                            else AUTOMATIC
                        }
                    }
                }
                safeMode == "opportunistic" -> AUTOMATIC
                else -> AUTOMATIC
            }
        }
    }
}
