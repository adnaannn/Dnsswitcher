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
        assertEquals("dns.adguard.com", com.example.manager.PrivateDnsHelper.normalizeHostname("adguard"))
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
}
