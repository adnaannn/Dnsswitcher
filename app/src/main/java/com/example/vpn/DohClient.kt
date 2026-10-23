package com.example.vpn

import android.net.VpnService
import android.util.Log
import com.example.model.DnsProfile
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

class DohClient(
    private val vpnService: VpnService
) {
    companion object {
        private const val TAG = "DohClient"
        private val DOH_MEDIA_TYPE = "application/dns-message".toMediaType()
    }

    // Protecting socket factory to ensure outbound DoH sockets bypass the VPN tunnel and avoid loops
    private val protectingSocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket {
            val socket = Socket()
            try {
                vpnService.protect(socket)
            } catch (e: Exception) {
                Log.w(TAG, "protect socket failed", e)
            }
            return socket
        }

        override fun createSocket(host: String?, port: Int): Socket {
            val socket = createSocket()
            socket.connect(java.net.InetSocketAddress(host, port))
            return socket
        }

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
            val socket = createSocket()
            if (localHost != null) {
                socket.bind(java.net.InetSocketAddress(localHost, localPort))
            }
            socket.connect(java.net.InetSocketAddress(host, port))
            return socket
        }

        override fun createSocket(host: InetAddress?, port: Int): Socket {
            val socket = createSocket()
            socket.connect(java.net.InetSocketAddress(host, port))
            return socket
        }

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
            val socket = createSocket()
            if (localAddress != null) {
                socket.bind(java.net.InetSocketAddress(localAddress, localPort))
            }
            socket.connect(java.net.InetSocketAddress(address, port))
            return socket
        }
    }

    // Bootstrap DNS resolver prevents circular DNS resolution for DoH endpoints
    private val bootstrapDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val clean = hostname.trim().lowercase()
            return when {
                clean.contains("adguard") -> listOfNotNull(
                    runCatching { InetAddress.getByName("94.140.14.14") }.getOrNull(),
                    runCatching { InetAddress.getByName("94.140.15.15") }.getOrNull(),
                    runCatching { InetAddress.getByName("2a10:50c0::ad1:ff") }.getOrNull(),
                    runCatching { InetAddress.getByName("2a10:50c0::ad2:ff") }.getOrNull()
                )
                clean == "1.1.1.1" || clean == "1.0.0.1" || clean == "one.one.one.one" || clean.contains("cloudflare") -> listOfNotNull(
                    runCatching { InetAddress.getByName("1.1.1.1") }.getOrNull(),
                    runCatching { InetAddress.getByName("1.0.0.1") }.getOrNull(),
                    runCatching { InetAddress.getByName("2606:4700:4700::1111") }.getOrNull(),
                    runCatching { InetAddress.getByName("2606:4700:4700::1001") }.getOrNull()
                )
                clean.contains("quad9") -> listOfNotNull(
                    runCatching { InetAddress.getByName("9.9.9.9") }.getOrNull(),
                    runCatching { InetAddress.getByName("149.112.112.112") }.getOrNull(),
                    runCatching { InetAddress.getByName("2620:fe::fe") }.getOrNull(),
                    runCatching { InetAddress.getByName("2620:fe::9") }.getOrNull()
                )
                else -> {
                    try {
                        Dns.SYSTEM.lookup(hostname)
                    } catch (e: Exception) {
                        listOfNotNull(runCatching { InetAddress.getByName("1.1.1.1") }.getOrNull())
                    }
                }
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .socketFactory(protectingSocketFactory)
        .dns(bootstrapDns)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun resolve(queryPayload: ByteArray, profile: DnsProfile): ByteArray? {
        val requestBody = queryPayload.toRequestBody(DOH_MEDIA_TYPE)
        val request = Request.Builder()
            .url(profile.dohUrl)
            .addHeader("Accept", "application/dns-message")
            .post(requestBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "DoH request to ${profile.name} returned HTTP ${response.code}")
                    null
                } else {
                    response.body?.bytes()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "DoH lookup failed on ${profile.name}: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in DoH lookup", e)
            null
        }
    }
}
