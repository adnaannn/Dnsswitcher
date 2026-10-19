package com.example.vpn

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

data class DnsQueryPacket(
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
        return sourcePort == other.sourcePort &&
                destPort == other.destPort &&
                sourceIp.contentEquals(other.sourceIp) &&
                destIp.contentEquals(other.destIp) &&
                dnsPayload.contentEquals(other.dnsPayload)
    }

    override fun hashCode(): Int {
        var result = sourceIp.contentHashCode()
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
        if (length < 28) return null // 20 bytes IPv4 + 8 bytes UDP min

        val version = (packet[0].toInt() and 0xF0) ushr 4
        if (version != 4) {
            // IPv6 or non-IP packet
            return null
        }

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
