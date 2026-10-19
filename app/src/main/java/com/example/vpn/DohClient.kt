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
        private val defaultFactory = getDefault()

        override fun createSocket(): Socket {
            val socket = defaultFactory.createSocket()
            try {
                vpnService.protect(socket)
            } catch (ignored: Exception) {}
            return socket
        }

        override fun createSocket(host: String?, port: Int): Socket {
            val socket = defaultFactory.createSocket()
            try {
                vpnService.protect(socket)
                if (host != null) {
                    socket.connect(java.net.InetSocketAddress(host, port), 5000)
                }
            } catch (ignored: Exception) {}
            return socket
        }

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
            val socket = defaultFactory.createSocket()
            try {
                if (localHost != null) {
                    socket.bind(java.net.InetSocketAddress(localHost, localPort))
                }
                vpnService.protect(socket)
                if (host != null) {
                    socket.connect(java.net.InetSocketAddress(host, port), 5000)
                }
            } catch (ignored: Exception) {}
            return socket
        }

        override fun createSocket(host: InetAddress?, port: Int): Socket {
            val socket = defaultFactory.createSocket()
            try {
                vpnService.protect(socket)
                if (host != null) {
                    socket.connect(java.net.InetSocketAddress(host, port), 5000)
                }
            } catch (ignored: Exception) {}
            return socket
        }

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
            val socket = defaultFactory.createSocket()
            try {
                if (localAddress != null) {
                    socket.bind(java.net.InetSocketAddress(localAddress, localPort))
                }
                vpnService.protect(socket)
                if (address != null) {
                    socket.connect(java.net.InetSocketAddress(address, port), 5000)
                }
            } catch (ignored: Exception) {}
            return socket
        }
    }

    // Bootstrap DNS resolver prevents circular DNS resolution for DoH endpoints
    private val bootstrapDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return when (hostname) {
                "dns.adguard-dns.com" -> listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(94.toByte(), 140.toByte(), 14.toByte(), 14.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(94.toByte(), 140.toByte(), 15.toByte(), 15.toByte()))
                )
                "cloudflare-dns.com", "one.one.one.one" -> listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(1, 1, 1, 1)),
                    InetAddress.getByAddress(hostname, byteArrayOf(1, 0, 0, 1))
                )
                "dns.quad9.net" -> listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(9, 9, 9, 9)),
                    InetAddress.getByAddress(hostname, byteArrayOf(149.toByte(), 112.toByte(), 112.toByte(), 112.toByte()))
                )
                else -> {
                    try {
                        Dns.SYSTEM.lookup(hostname)
                    } catch (e: Exception) {
                        listOf(InetAddress.getByAddress(hostname, byteArrayOf(1, 1, 1, 1)))
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
