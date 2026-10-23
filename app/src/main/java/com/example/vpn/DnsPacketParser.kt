package com.example.vpn

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

data class DnsQueryPacket(
    val isIpv6: Boolean = false,
    val sourceIp: ByteArray,
    val destIp: ByteArray,
    val sourcePort: Int,
    val destPort: Int,
    val dnsPayload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DnsQueryPacket
        return isIpv6 == other.isIpv6 &&
                sourcePort == other.sourcePort &&
                destPort == other.destPort &&
                sourceIp.contentEquals(other.sourceIp) &&
                destIp.contentEquals(other.destIp) &&
                dnsPayload.contentEquals(other.dnsPayload)
    }

    override fun hashCode(): Int {
        var result = isIpv6.hashCode()
        result = 31 * result + sourceIp.contentHashCode()
        result = 31 * result + destIp.contentHashCode()
        result = 31 * result + sourcePort
        result = 31 * result + destPort
        result = 31 * result + dnsPayload.contentHashCode()
        return result
    }
}

object DnsPacketParser {
    private val packetIdCounter = AtomicInteger(1000)

    fun parseUdpDnsQuery(packet: ByteArray, length: Int): DnsQueryPacket? {
        if (length < 28) return null

        val version = (packet[0].toInt() and 0xF0) ushr 4
        return when (version) {
            4 -> parseIpv4UdpDnsQuery(packet, length)
            6 -> parseIpv6UdpDnsQuery(packet, length)
            else -> null
        }
    }

    private fun parseIpv4UdpDnsQuery(packet: ByteArray, length: Int): DnsQueryPacket? {
        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (length < ipHeaderLen + 8) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) { // 17 = UDP
            return null
        }

        val sourceIp = packet.copyOfRange(12, 16)
        val destIp = packet.copyOfRange(16, 20)

        val udpOffset = ipHeaderLen
        val sourcePort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
        val destPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)

        // Standard DNS port is 53
        if (destPort != 53) return null

        val udpLen = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or (packet[udpOffset + 5].toInt() and 0xFF)
        val dnsPayloadLen = udpLen - 8
        if (dnsPayloadLen <= 0 || udpOffset + 8 + dnsPayloadLen > length) return null

        val dnsPayload = packet.copyOfRange(udpOffset + 8, udpOffset + 8 + dnsPayloadLen)

        return DnsQueryPacket(
            isIpv6 = false,
            sourceIp = sourceIp,
            destIp = destIp,
            sourcePort = sourcePort,
            destPort = destPort,
            dnsPayload = dnsPayload
        )
    }

    private fun parseIpv6UdpDnsQuery(packet: ByteArray, length: Int): DnsQueryPacket? {
        val ipHeaderLen = 40
        if (length < ipHeaderLen + 8) return null

        val nextHeader = packet[6].toInt() and 0xFF
        if (nextHeader != 17) { // 17 = UDP
            return null
        }

        val sourceIp = packet.copyOfRange(8, 24)
        val destIp = packet.copyOfRange(24, 40)

        val udpOffset = ipHeaderLen
        val sourcePort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
        val destPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)

        if (destPort != 53) return null

        val udpLen = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or (packet[udpOffset + 5].toInt() and 0xFF)
        val dnsPayloadLen = udpLen - 8
        if (dnsPayloadLen <= 0 || udpOffset + 8 + dnsPayloadLen > length) return null

        val dnsPayload = packet.copyOfRange(udpOffset + 8, udpOffset + 8 + dnsPayloadLen)

        return DnsQueryPacket(
            isIpv6 = true,
            sourceIp = sourceIp,
            destIp = destIp,
            sourcePort = sourcePort,
            destPort = destPort,
            dnsPayload = dnsPayload
        )
    }

    fun buildUdpDnsResponse(
        originalQuery: DnsQueryPacket,
        dnsResponsePayload: ByteArray
    ): ByteArray {
        // RFC 8484 Compliance: Map DoH response to original DNS query by replacing
        // response transaction ID (bytes 0-1) with the original query's transaction ID.
        val matchedPayload = dnsResponsePayload.copyOf()
        if (matchedPayload.size >= 2 && originalQuery.dnsPayload.size >= 2) {
            matchedPayload[0] = originalQuery.dnsPayload[0]
            matchedPayload[1] = originalQuery.dnsPayload[1]
        }

        return if (originalQuery.isIpv6) {
            buildIpv6UdpDnsResponse(originalQuery, matchedPayload)
        } else {
            buildIpv4UdpDnsResponse(originalQuery, matchedPayload)
        }
    }

    private fun buildIpv4UdpDnsResponse(
        originalQuery: DnsQueryPacket,
        dnsResponsePayload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + dnsResponsePayload.size

        val packet = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(packet)

        // 1. IPv4 Header
        buffer.put(0x45.toByte()) // Version 4, IHL 5 (20 bytes)
        buffer.put(0x00.toByte()) // DSCP/ECN
        buffer.putShort(totalLen.toShort()) // Total Length
        buffer.putShort(packetIdCounter.getAndIncrement().toShort()) // Identification
        buffer.putShort(0x4000.toShort()) // Flags: Don't Fragment
        buffer.put(64.toByte()) // TTL
        buffer.put(17.toByte()) // Protocol: UDP (17)
        buffer.putShort(0.toShort()) // Checksum placeholder

        // Source IP is original query's destination IP
        buffer.put(originalQuery.destIp)
        // Destination IP is original query's source IP
        buffer.put(originalQuery.sourceIp)

        // Calculate and write IPv4 header checksum
        val ipChecksum = calculateChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // 2. UDP Header
        buffer.position(ipHeaderLen)
        buffer.putShort(originalQuery.destPort.toShort()) // Source port: 53
        buffer.putShort(originalQuery.sourcePort.toShort()) // Destination port
        buffer.putShort((udpHeaderLen + dnsResponsePayload.size).toShort()) // UDP Length
        buffer.putShort(0.toShort()) // UDP Checksum (0 is valid for IPv4 UDP)

        // 3. DNS Response Payload
        buffer.put(dnsResponsePayload)

        return packet
    }

    private fun buildIpv6UdpDnsResponse(
        originalQuery: DnsQueryPacket,
        dnsResponsePayload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 40
        val udpHeaderLen = 8
        val udpPayloadLen = udpHeaderLen + dnsResponsePayload.size
        val totalLen = ipHeaderLen + udpPayloadLen

        val packet = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(packet)

        // 1. IPv6 Header (40 bytes)
        buffer.put(0x60.toByte()) // Version 6, Traffic Class upper nibble 0
        buffer.put(0x00.toByte()) // Traffic Class lower nibble 0, Flow Label upper nibble 0
        buffer.putShort(0.toShort()) // Flow Label lower 16 bits
        buffer.putShort(udpPayloadLen.toShort()) // Payload Length
        buffer.put(17.toByte()) // Next Header: UDP (17)
        buffer.put(64.toByte()) // Hop Limit: 64

        // Source IP is original query's destination IP (16 bytes)
        buffer.put(originalQuery.destIp)
        // Destination IP is original query's source IP (16 bytes)
        buffer.put(originalQuery.sourceIp)

        // 2. UDP Header
        val udpOffset = ipHeaderLen
        buffer.position(udpOffset)
        buffer.putShort(originalQuery.destPort.toShort()) // Source port: 53
        buffer.putShort(originalQuery.sourcePort.toShort()) // Destination port
        buffer.putShort(udpPayloadLen.toShort()) // UDP Length
        buffer.putShort(0.toShort()) // Checksum placeholder

        // 3. DNS Payload
        buffer.put(dnsResponsePayload)

        // 4. Calculate IPv6 UDP Checksum (mandatory in IPv6)
        val udpChecksum = calculateIpv6UdpChecksum(
            srcIp = originalQuery.destIp,
            dstIp = originalQuery.sourceIp,
            udpPacket = packet,
            udpOffset = udpOffset,
            udpLength = udpPayloadLen
        )
        val finalChecksum = if (udpChecksum == 0) 0xFFFF else udpChecksum
        packet[udpOffset + 6] = ((finalChecksum ushr 8) and 0xFF).toByte()
        packet[udpOffset + 7] = (finalChecksum and 0xFF).toByte()

        return packet
    }

    private fun calculateIpv6UdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpPacket: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0

        // Pseudo header: Src IP (16 bytes = 8 words)
        for (i in 0 until 16 step 2) {
            val word = ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            sum += word
        }
        // Pseudo header: Dst IP (16 bytes = 8 words)
        for (i in 0 until 16 step 2) {
            val word = ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
            sum += word
        }
        // Pseudo header: UDP Length (32 bit = 2 words)
        sum += (udpLength shr 16) and 0xFFFF
        sum += udpLength and 0xFFFF

        // Pseudo header: Next Header (17 = UDP)
        sum += 17

        // UDP Header + Data
        var i = udpOffset
        val end = udpOffset + udpLength
        while (i < end - 1) {
            val word = ((udpPacket[i].toInt() and 0xFF) shl 8) or (udpPacket[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (udpPacket[i].toInt() and 0xFF) shl 8
        }

        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            val b1 = data[i].toInt() and 0xFF
            val b2 = data[i + 1].toInt() and 0xFF
            val word = (b1 shl 8) or b2
            sum += word
            i += 2
        }
        if (i < offset + length) {
            val b1 = data[i].toInt() and 0xFF
            sum += (b1 shl 8)
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
