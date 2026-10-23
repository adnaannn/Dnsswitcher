# Implementation Plan: Restoring Internet & Wi-Fi Connectivity in Zero-Command Mode

## Problem Analysis
When enabling Zero-Command Mode with AdGuard, Cloudflare, or Quad9, the device displays an exclamation mark on the Wi-Fi / Mobile Data icon indicating "No internet access". Through deep code inspection, four root causes were identified:

1. **Underlying Network Feedback Loop in `DnsVpnService`**:
   The `NetworkRequest` used by `ConnectivityManager.NetworkCallback` registered for `NET_CAPABILITY_INTERNET` without requiring `NET_CAPABILITY_NOT_VPN`. When `DnsVpnService` started and established its VPN interface, the network callback intercepted the newly created VPN network itself and called `setUnderlyingNetworks(arrayOf(vpnNetwork))`. This created a recursive loop where the VPN pointed to itself as the underlying network, immediately severing all internet access on Wi-Fi and cellular connections.

2. **DNS Transaction ID Mismatch (RFC 8484 Violation) in `DnsPacketParser`**:
   Per RFC 8484 Section 4.2.1, DoH servers (including Cloudflare and AdGuard) often return DNS responses with Transaction ID `0x0000` or arbitrary values. The DNS proxy is required to overwrite the response's Transaction ID with the query's original Transaction ID before returning it to the client. Because `buildUdpDnsResponse()` did not restore the original Transaction ID, Android's local stub resolver (`netd`) dropped every DNS response as invalid, causing all system connectivity checks (`connectivitycheck.gstatic.com`) to time out.

3. **Dual-Stack IPv6 Blackholing**:
   Most modern cellular and Wi-Fi networks operate in dual-stack mode (IPv4 + IPv6). The VPN only configured an IPv4 virtual DNS server (`10.111.222.1`), and `DnsPacketParser` discarded all non-IPv4 packets (`version != 4`). System connectivity probes and apps attempting IPv6 resolution stalled, triggering Android's unvalidated network warning.

4. **Missing App Self-Exclusion**:
   `DnsVpnService.Builder` did not call `addDisallowedApplication(packageName)`. Outbound DoH and diagnostic queries could inadvertently be subjected to VPN socket routing constraints.

---

## Proposed Changes

### 1. `com/example/vpn/DnsVpnService.kt`
- **Filter Underlying Networks**: Update `NetworkRequest` to require `NET_CAPABILITY_NOT_VPN` and `NET_CAPABILITY_INTERNET`. In `onAvailable()`, ensure only physical non-VPN networks (Wi-Fi, Cellular, Ethernet) are passed to `setUnderlyingNetworks()`.
- **Safe Initial Underlying Network**: Pass `null` to `setUnderlyingNetworks()` at startup to let Android automatically bind the system default physical network, preventing loopbacks.
- **Disallow App Package**: Call `builder.addDisallowedApplication(packageName)` so the app's own HTTPS DoH queries and diagnostic tests bypass the TUN interface directly.
- **Dual-Stack DNS Configuration**: Configure both IPv4 (`10.111.222.1/24`, route `10.111.222.1/32`) and IPv6 (`fd00:1:fd00:1::1/128`, route `fd00:1:fd00:1::1/128`) virtual DNS endpoints.

### 2. `com/example/vpn/DnsPacketParser.kt`
- **Transaction ID Restoration**: In `buildUdpDnsResponse()`, copy the original 2-byte Transaction ID from the query packet into the DoH response payload before sending it back through the TUN interface.
- **IPv6 UDP DNS Parsing & Response**: Add parsing for IPv6 UDP packets (version 6, Next Header 17, destPort 53) and construct valid IPv6 UDP DNS responses with computed UDP pseudo-header checksums.

### 3. `com/example/vpn/DohClient.kt`
- **Resilient Upstream Endpoints**: Enhance `bootstrapDns` with static IP mapping for all built-in providers (AdGuard `94.140.14.14`/`94.140.15.15`, Cloudflare `1.1.1.1`/`1.0.0.1`, Quad9 `9.9.9.9`/`149.112.112.112`).
- **Direct IP Fallback**: Support direct IP URL fallback if hostname resolution fails under degraded network conditions.

---

## Verification Plan

### Automated Verification
1. Run local Robolectric / unit tests to verify:
   - `DnsPacketParser` preserves query Transaction IDs in responses.
   - IPv4 and IPv6 UDP DNS queries and responses are correctly structured and checksummed.
   - Command: `gradle :app:testDebugUnitTest`
2. Run `compile_applet` to ensure successful compilation and packaging of the debug APK.

### Manual / Device Verification
1. Start Zero-Command VPN mode with AdGuard, Cloudflare, and Quad9.
2. Confirm the Android status bar Wi-Fi and mobile data icons show active data transmission without an exclamation mark or "No internet" notice.
3. Run the in-app DNS diagnostic test to verify resolution of `google.com`, `one.one.one.one`, and provider domains.
