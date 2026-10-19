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
        assertEquals("dns.adguard-dns.com", DnsProvider.ADGUARD.hostname)
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
        assertEquals(DnsProvider.ADGUARD, DnsProvider.fromSpecifier("hostname", "dns.adguard-dns.com"))
        assertEquals(DnsProvider.CLOUDFLARE, DnsProvider.fromSpecifier("hostname", "one.one.one.one"))
        assertEquals(DnsProvider.QUAD9, DnsProvider.fromSpecifier("hostname", "dns.quad9.net"))
        assertEquals(DnsProvider.AUTOMATIC, DnsProvider.fromSpecifier("opportunistic", ""))
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
    fun `verify MainActivity launches without crash`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }
}
