# Implementation Plan: Restoring Internet & Quick Settings DNS Switching

## Problem Analysis & Root Cause Identification
1. **Root Cause of "Internet Not Coming & Exclamation Mark on Wi-Fi/Data"**:
   - In Linux / Android kernel networking, any IP address assigned to an interface via `addAddress()` is registered in `table local` (local loopback delivery).
   - In `DnsVpnService.kt`, both `addAddress(VIRTUAL_DNS_IP)` and `addDnsServer(VIRTUAL_DNS_IP)` were set to `10.111.222.1`. Consequently, when Android's DNS resolver sent UDP queries to `10.111.222.1:53`, the kernel delivered them locally to `lo` (where no socket was listening) rather than forwarding them through the TUN character device.
   - Because `DnsVpnService` never received the queries, Android's `NetworkMonitor` probe for `connectivitycheck.gstatic.com` timed out, displaying the exclamation mark ("No internet") on Wi-Fi and Mobile Data.
   - **Fix**: Separated the TUN interface IP (`TUN_INTERFACE_IPV4 = "10.111.222.10"`, `TUN_INTERFACE_IPV6 = "fd00:1:fd00:1::10"`) from the virtual DNS server IP (`VIRTUAL_DNS_IP = "10.111.222.1"`, `VIRTUAL_DNS_IPV6 = "fd00:1:fd00:1::1"`). Queries destined for the virtual DNS server enter the TUN device and are promptly resolved by `DohClient`.

2. **Root Cause of "Internet Not Coming While Switching"**:
   - When switching profiles, OkHttp maintained keep-alive connections to previous upstream servers in its connection pool, causing subsequent queries to stall if routes changed or under transient state renegotiation.
   - Android's `ConnectivityService` retained stale DNS failure states until a slow background timeout.
   - **Fix**: Added `clearConnectionPool()` in `DohClient` on profile switch and actively invoked `PrivateDnsHelper.triggerNetworkRevalidation(context)` during profile switches to force netd to immediately re-evaluate connectivity and unblock traffic.
   - Updated AdGuard DoH URL from legacy `dns.adguard.com` to current official `https://dns.adguard-dns.com/dns-query`.

3. **Quick Settings Tile Implementation (`DnsTileService`)**:
   - Registered `DnsTileService` in `AndroidManifest.xml` with permission `BIND_QUICK_SETTINGS_TILE`.
   - Wired click handling to cycle through providers respecting user's configured `cycleMode` (2-state, 3-state, or 5-state).
   - Dynamically updates tile appearance: label, subtitle ("System Default", hostname, or "Off"), active/inactive state, and provider icon.
   - Supports both Zero-Command VPN mode and System Private DNS mode without requiring the app to be opened.

---

## Verification
- Unit & Robolectric tests (`gradle :app:testDebugUnitTest`) pass verifying dual-stack configuration, AdGuard DoH URL, DnsTileService tile update, and DNS packet parsing.
- Verified app compilation with `compile_applet`.
