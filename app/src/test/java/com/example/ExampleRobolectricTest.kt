package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppPreferences
import com.example.data.DnsProvider
import com.example.manager.DnsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches DNS Switcher`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DNS Switcher", appName)
    }

    @Test
    fun `verify DNS providers hostnames and modes`() {
        assertEquals("dns.adguard.com", DnsProvider.ADGUARD.hostname)
        assertEquals("hostname", DnsProvider.ADGUARD.mode)

        assertEquals("one.one.one.one", DnsProvider.CLOUDFLARE.hostname)
        assertEquals("hostname", DnsProvider.CLOUDFLARE.mode)

        assertEquals("dns.quad9.net", DnsProvider.QUAD9.hostname)
        assertEquals("hostname", DnsProvider.QUAD9.mode)

        assertEquals("", DnsProvider.AUTOMATIC.hostname)
        assertEquals("opportunistic", DnsProvider.AUTOMATIC.mode)

        assertEquals("", DnsProvider.OFF.hostname)
        assertEquals("off", DnsProvider.OFF.mode)
    }

    @Test
    fun `verify five-state cycle ordering`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppPreferences.getInstance(context).cycleMode = AppPreferences.CYCLE_FIVE_STATE

        // Off -> AdGuard -> Cloudflare -> Quad9 -> Automatic -> Off
        val step1 = DnsManager.getNextProviderInCycle(context, DnsProvider.OFF)
        assertEquals(DnsProvider.ADGUARD, step1)

        val step2 = DnsManager.getNextProviderInCycle(context, step1)
        assertEquals(DnsProvider.CLOUDFLARE, step2)

        val step3 = DnsManager.getNextProviderInCycle(context, step2)
        assertEquals(DnsProvider.QUAD9, step3)

        val step4 = DnsManager.getNextProviderInCycle(context, step3)
        assertEquals(DnsProvider.AUTOMATIC, step4)

        val step5 = DnsManager.getNextProviderInCycle(context, step4)
        assertEquals(DnsProvider.OFF, step5)
    }

    @Test
    fun `verify ADB command formatting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cmd = DnsManager.getAdbCommand(context)
        assertTrue(cmd.startsWith("adb shell pm grant"))
        assertTrue(cmd.endsWith("android.permission.WRITE_SECURE_SETTINGS"))
        assertTrue(cmd.contains(context.packageName))
    }

    @Test
    fun `verify DnsProvider fromSpecifier resolution`() {
        assertEquals(DnsProvider.OFF, DnsProvider.fromSpecifier("off", ""))
        assertEquals(DnsProvider.ADGUARD, DnsProvider.fromSpecifier("hostname", "dns.adguard.com"))
        assertEquals(DnsProvider.ADGUARD, DnsProvider.fromSpecifier("hostname", "dns.adguard-dns.com"))
        assertEquals(DnsProvider.CLOUDFLARE, DnsProvider.fromSpecifier("hostname", "one.one.one.one"))
        assertEquals(DnsProvider.CLOUDFLARE, DnsProvider.fromSpecifier("hostname", "1.1.1.1"))
        assertEquals(DnsProvider.CLOUDFLARE, DnsProvider.fromSpecifier("hostname", "1.0.0.1"))
        assertEquals(DnsProvider.CLOUDFLARE, DnsProvider.fromSpecifier("hostname", "1dot1dot1dot1.cloudflare-dns.com"))
        assertEquals(DnsProvider.QUAD9, DnsProvider.fromSpecifier("hostname", "dns.quad9.net"))
        assertEquals(DnsProvider.QUAD9, DnsProvider.fromSpecifier("hostname", "9.9.9.9"))
        assertEquals(DnsProvider.AUTOMATIC, DnsProvider.fromSpecifier("opportunistic", ""))
    }

    @Test
    fun `verify hostname normalization handles URLs and IPs`() {
        assertEquals("one.one.one.one", com.example.manager.PrivateDnsHelper.normalizeHostname("1.1.1.1"))
        assertEquals("one.one.one.one", com.example.manager.PrivateDnsHelper.normalizeHostname("one.one.one.one"))
        assertEquals("one.one.one.one", com.example.manager.PrivateDnsHelper.normalizeHostname("cloudflare"))
        assertEquals("one.one.one.one", com.example.manager.PrivateDnsHelper.normalizeHostname("cloud fare 1.1.1.1"))
        assertEquals("dns.adguard.com", com.example.manager.PrivateDnsHelper.normalizeHostname("adguard"))
        assertEquals("dns.adguard.com", com.example.manager.PrivateDnsHelper.normalizeHostname("dns.adguard.com"))
        assertEquals("dns.adguard.com", com.example.manager.PrivateDnsHelper.normalizeHostname("https://dns.adguard.com/dns-query"))
        assertEquals("dns.quad9.net", com.example.manager.PrivateDnsHelper.normalizeHostname("dns.quad9.net:853"))
        assertEquals("dns.quad9.net", com.example.manager.PrivateDnsHelper.normalizeHostname("9.9.9.9"))
    }

    @Test
    fun `verify engineMode preferences default and update`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferences.getInstance(context)
        assertEquals(AppPreferences.ENGINE_LOCAL_DNS, prefs.engineMode)
        prefs.engineMode = AppPreferences.ENGINE_SYSTEM_PRIVATE_DNS
        assertEquals(AppPreferences.ENGINE_SYSTEM_PRIVATE_DNS, prefs.engineMode)
        prefs.engineMode = AppPreferences.ENGINE_LOCAL_DNS
    }

    @Test
    fun `verify PrivateDnsHelper constants and config model`() {
        assertEquals("private_dns_mode", com.example.manager.PrivateDnsHelper.PRIVATE_DNS_MODE)
        assertEquals("private_dns_specifier", com.example.manager.PrivateDnsHelper.PRIVATE_DNS_SPECIFIER)
        assertEquals("off", com.example.manager.PrivateDnsHelper.MODE_OFF)
        assertEquals("opportunistic", com.example.manager.PrivateDnsHelper.MODE_OPPORTUNISTIC)
        assertEquals("hostname", com.example.manager.PrivateDnsHelper.MODE_PROVIDER_HOSTNAME)

        val offConfig = com.example.manager.PrivateDnsHelper.PrivateDnsConfig("off", "")
        assertTrue(offConfig.isOff)
        assertEquals("DNS Off", offConfig.providerName)

        val autoConfig = com.example.manager.PrivateDnsHelper.PrivateDnsConfig("opportunistic", "")
        assertTrue(autoConfig.isOpportunistic)
        assertEquals("Automatic", autoConfig.providerName)

        val adguardConfig = com.example.manager.PrivateDnsHelper.PrivateDnsConfig("hostname", "dns.adguard-dns.com")
        assertTrue(adguardConfig.isHostnameMode)
        assertEquals("AdGuard DNS", adguardConfig.providerName)

        val cloudflareConfig = com.example.manager.PrivateDnsHelper.PrivateDnsConfig("hostname", "one.one.one.one")
        assertTrue(cloudflareConfig.isHostnameMode)
        assertEquals("Cloudflare DNS", cloudflareConfig.providerName)

        val quad9Config = com.example.manager.PrivateDnsHelper.PrivateDnsConfig("hostname", "dns.quad9.net")
        assertTrue(quad9Config.isHostnameMode)
        assertEquals("Quad9 DNS", quad9Config.providerName)
    }

    @Test
    fun `verify MainActivity launches without crash`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }

    @Test
    fun `verify DnsDiagnosticRunner resolves localhost successfully`() = kotlinx.coroutines.runBlocking {
        val result = com.example.manager.DnsDiagnosticRunner.testDnsResolution(
            targetDomain = "localhost",
            maxRetries = 1
        )
        assertTrue(result is com.example.data.DnsDiagnosticResult.Success)
        val success = result as com.example.data.DnsDiagnosticResult.Success
        assertEquals("localhost", success.targetHost)
        assertTrue(success.latencyMs >= 0)
    }

    @Test
    fun `verify DnsDiagnosticRunner handles resolution failure gracefully`() = kotlinx.coroutines.runBlocking {
        val result = com.example.manager.DnsDiagnosticRunner.testDnsResolution(
            targetDomain = "invalid.domain.that.does.not.exist.test",
            maxRetries = 1,
            retryDelayMs = 10
        )
        assertTrue(result is com.example.data.DnsDiagnosticResult.Failure)
        val failure = result as com.example.data.DnsDiagnosticResult.Failure
        assertEquals("invalid.domain.that.does.not.exist.test", failure.targetHost)
        assertTrue(failure.errorMessage.isNotEmpty())
    }

    @Test
    fun `verify DnsPacketParser parses IPv4 DNS query and preserves transaction ID in response`() {
        val queryDnsPayload = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val rawIpv4Packet = ByteArray(20 + 8 + queryDnsPayload.size)
        rawIpv4Packet[0] = 0x45.toByte() // IPv4, header len 20
        rawIpv4Packet[9] = 17.toByte() // UDP
        // Source IP 10.0.0.5
        System.arraycopy(byteArrayOf(10, 0, 0, 5), 0, rawIpv4Packet, 12, 4)
        // Dest IP 10.111.222.1
        System.arraycopy(byteArrayOf(10, 111, 222.toByte(), 1), 0, rawIpv4Packet, 16, 4)
        // UDP Src Port 12345
        rawIpv4Packet[20] = (12345 ushr 8).toByte()
        rawIpv4Packet[21] = (12345 and 0xFF).toByte()
        // UDP Dst Port 53
        rawIpv4Packet[22] = 0
        rawIpv4Packet[23] = 53
        // UDP Length 20
        rawIpv4Packet[24] = 0
        rawIpv4Packet[25] = 20
        // DNS Payload
        System.arraycopy(queryDnsPayload, 0, rawIpv4Packet, 28, queryDnsPayload.size)

        val query = com.example.vpn.DnsPacketParser.parseUdpDnsQuery(rawIpv4Packet, rawIpv4Packet.size)
        assertNotNull(query)
        assertEquals(false, query?.isIpv6)
        assertEquals(12345, query?.sourcePort)
        assertEquals(53, query?.destPort)

        // Simulate DoH response where server returned ID 0x0000 (RFC 8484 standard behavior)
        val dohResponsePayload = byteArrayOf(0x00, 0x00, 0x81.toByte(), 0x80.toByte(), 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00)
        val responsePacket = com.example.vpn.DnsPacketParser.buildUdpDnsResponse(query!!, dohResponsePayload)

        assertTrue(responsePacket.size > 28)
        // Check destination IP in response matches original source IP
        assertEquals(10.toByte(), responsePacket[16])
        assertEquals(0.toByte(), responsePacket[17])
        assertEquals(0.toByte(), responsePacket[18])
        assertEquals(5.toByte(), responsePacket[19])
        // Check destination port in response matches original source port
        val respDstPort = ((responsePacket[22].toInt() and 0xFF) shl 8) or (responsePacket[23].toInt() and 0xFF)
        assertEquals(12345, respDstPort)
        // Check transaction ID in returned DNS payload has been restored to 0x1234
        assertEquals(0x12.toByte(), responsePacket[28])
        assertEquals(0x34.toByte(), responsePacket[29])
    }

    @Test
    fun `verify DnsPacketParser parses IPv6 DNS query and builds valid IPv6 UDP response`() {
        val queryDnsPayload = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val rawIpv6Packet = ByteArray(40 + 8 + queryDnsPayload.size)
        rawIpv6Packet[0] = 0x60.toByte() // IPv6
        rawIpv6Packet[6] = 17.toByte() // Next Header: UDP
        // Source IPv6 fd00::5
        rawIpv6Packet[8] = 0xFD.toByte()
        rawIpv6Packet[23] = 0x05
        // Dest IPv6 fd00:1:fd00:1::1
        rawIpv6Packet[24] = 0xFD.toByte()
        rawIpv6Packet[39] = 0x01
        // UDP Src Port 54321
        rawIpv6Packet[40] = (54321 ushr 8).toByte()
        rawIpv6Packet[41] = (54321 and 0xFF).toByte()
        // UDP Dst Port 53
        rawIpv6Packet[42] = 0
        rawIpv6Packet[43] = 53
        // UDP Length 20
        rawIpv6Packet[44] = 0
        rawIpv6Packet[45] = 20
        // DNS Payload
        System.arraycopy(queryDnsPayload, 0, rawIpv6Packet, 48, queryDnsPayload.size)

        val query = com.example.vpn.DnsPacketParser.parseUdpDnsQuery(rawIpv6Packet, rawIpv6Packet.size)
        assertNotNull(query)
        assertEquals(true, query?.isIpv6)
        assertEquals(54321, query?.sourcePort)
        assertEquals(53, query?.destPort)

        val dohResponsePayload = byteArrayOf(0x00, 0x00, 0x81.toByte(), 0x80.toByte(), 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00)
        val responsePacket = com.example.vpn.DnsPacketParser.buildUdpDnsResponse(query!!, dohResponsePayload)

        assertTrue(responsePacket.size >= 48)
        // IPv6 version 6
        assertEquals(0x60.toByte(), responsePacket[0])
        // Destination port is 54321
        val respDstPort = ((responsePacket[42].toInt() and 0xFF) shl 8) or (responsePacket[43].toInt() and 0xFF)
        assertEquals(54321, respDstPort)
        // Transaction ID is restored to 0xABCD
        assertEquals(0xAB.toByte(), responsePacket[48])
        assertEquals(0xCD.toByte(), responsePacket[49])
    }

    @Test
    fun `verify DnsVpnService dual-stack IPv4 and IPv6 network prefix configurations`() {
        // IPv4 Dual-Stack configuration constants
        assertEquals("10.111.222.10", com.example.vpn.DnsVpnService.TUN_INTERFACE_IPV4)
        assertEquals("10.111.222.1", com.example.vpn.DnsVpnService.VIRTUAL_DNS_IP)
        assertEquals("10.111.222.0", com.example.vpn.DnsVpnService.VIRTUAL_DNS_IPV4_SUBNET)
        assertEquals(24, com.example.vpn.DnsVpnService.VIRTUAL_DNS_IPV4_PREFIX)
        assertEquals(32, com.example.vpn.DnsVpnService.VIRTUAL_DNS_IPV4_ROUTE_PREFIX)

        // IPv6 Dual-Stack configuration constants
        assertEquals("fd00:1:fd00:1::10", com.example.vpn.DnsVpnService.TUN_INTERFACE_IPV6)
        assertEquals("fd00:1:fd00:1::1", com.example.vpn.DnsVpnService.VIRTUAL_DNS_IPV6)
        assertEquals("fd00:1:fd00:1::", com.example.vpn.DnsVpnService.VIRTUAL_DNS_IPV6_SUBNET)
        assertEquals(64, com.example.vpn.DnsVpnService.VIRTUAL_DNS_IPV6_PREFIX)
        assertEquals(64, com.example.vpn.DnsVpnService.VIRTUAL_DNS_IPV6_ROUTE_PREFIX)
    }

    @Test
    fun `verify AdGuard DoH URL uses official dns-query endpoint`() {
        assertEquals("https://dns.adguard-dns.com/dns-query", com.example.model.DnsProfile.ADGUARD.dohUrl)
        assertEquals("https://cloudflare-dns.com/dns-query", com.example.model.DnsProfile.CLOUDFLARE.dohUrl)
        assertEquals("https://dns.quad9.net/dns-query", com.example.model.DnsProfile.QUAD9.dohUrl)
    }

    @Test
    fun `verify DnsTileService requestTileUpdate runs without exception`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.service.DnsTileService.requestTileUpdate(context)
    }
}
