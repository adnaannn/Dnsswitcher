# DNS Switcher

**DNS Switcher** is a native Android application and Quick Settings tile that lets you switch Android Private DNS between **AdGuard DNS**, **Cloudflare DNS**, **Quad9 DNS**, **Automatic**, and **Off** directly from the Android Quick Settings panel, just like a built-in system tile.

---

## Features

1. **Native Quick Settings Tile**:
   - Single tap rotates through providers:
     **Off** ➔ **AdGuard DNS** ➔ **Cloudflare DNS** ➔ **Quad9 DNS** ➔ **Automatic DNS** ➔ **Off** (or 2-way / 3-way cycle customizable in Settings).
   - Tile accurately reflects the real Android system Private DNS state (`Settings.Global`).
   - Displays custom provider icon, name, and active/inactive state.
   - Prevents duplicate/rapid overlapping taps safely with concurrency protection.
   - Persists across app restarts and device reboots via `Settings.Global` and `BootReceiver`.

2. **Standard Android Private DNS (DoT)**:
   - **AdGuard DNS**: `dns.adguard-dns.com` (blocks ads, trackers, malware)
   - **Cloudflare DNS**: `one.one.one.one` (ultra-fast, zero-logging 1.1.1.1)
   - **Quad9 DNS**: `dns.quad9.net` (threat intelligence and botnet protection)
   - **Automatic**: Opportunistic DNS-over-TLS (Android system default)
   - **Off**: Disabled Private DNS (falls back to network unencrypted DNS)

3. **System-Level Verification**:
   - The app verifies that the system setting was actually committed before claiming success.
   - If writing fails or permission is missing, the previous valid configuration is preserved.

4. **Live Diagnostics**:
   - Real-time display of `private_dns_mode` and `private_dns_specifier`.
   - On-demand DNS resolution latency testing without tracking or logging queries.

5. **Material 3 Design**:
   - Modern dark/light theme inspired by Google Material 3.
   - Large hero toggle button, individual provider cards with one-tap copy and activation.

---

## Zero-Command Mode vs. System Private DNS Mode

You can choose between two switching engines directly in the app:

### 1. ⚡ No-Command Mode (Recommended — 100% on Phone)
- **Zero ADB, zero PC, zero cables, zero command lines required.**
- Uses Android's built-in `VpnService` permission.
- Android shows a standard on-screen system prompt: *"DNS Switcher wants to set up a VPN connection. [OK]"*.
- Tap **OK once on your screen**, and you are completely set up!
- The Quick Settings tile and in-app button will now seamlessly cycle:
  **DNS Off ➔ AdGuard DNS ➔ Cloudflare DNS ➔ Quad9 DNS ➔ Automatic ➔ DNS Off**!
- Only DNS queries (port 53) are routed to your selected encrypted DNS-over-HTTPS (DoH) provider. Your actual browsing traffic is never rerouted.

### 2. ⚙️ System Private DNS Mode (Requires One-Time ADB)
- For users who prefer setting Android's global `Settings.Global` Private DNS (DoT) at the OS level.
- Requires running one command from a PC terminal via USB debugging:
  ```bash
  adb shell pm grant com.aistudio.dnsswitcher.kzqmnv android.permission.WRITE_SECURE_SETTINGS
  ```

### Using Shizuku / Wireless Debugging (On-Device)

If you use [Shizuku](https://shizuku.rikka.app/), you can execute the command directly on-device using a terminal app like Termux without a PC:

```bash
rish -c "pm grant com.aistudio.dnsswitcher.kzqmnv android.permission.WRITE_SECURE_SETTINGS"
```

---

## Adding the Quick Settings Tile

- **Android 13+**: Tap **Add Tile to Quick Settings** directly inside the app.
- **Android 9 - 12**:
  1. Swipe down twice from the top of your screen to expand the Quick Settings panel.
  2. Tap the **Edit** (pencil) icon.
  3. Locate the **DNS Switcher** tile and drag it to your active tiles.
  4. Tap the tile anytime to alternate DNS providers!

---

## Build & Testing

### Build Debug APK
```bash
gradle assembleDebug
```

### Run Unit and Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
