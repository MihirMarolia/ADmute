package com.example.admutetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdHostsTest {

    @Test
    fun `matches known ad hosts and their subdomains`() {
        assertTrue(AdHosts.isAdHost("doubleclick.net"))
        assertTrue(AdHosts.isAdHost("pubads.g.doubleclick.net"))
        assertTrue(AdHosts.isAdHost("IMASDK.googleapis.com"))
        assertTrue(AdHosts.isAdHost("s0.2mdn.net."))
    }

    @Test
    fun `does not match unrelated hosts`() {
        assertFalse(AdHosts.isAdHost("www.youtube.com"))
        assertFalse(AdHosts.isAdHost("notdoubleclick.net.example.com"))
        assertFalse(AdHosts.isAdHost("googleapis.com"))
    }
}

class DnsQuestionTest {

    @Test
    fun `reads the question host name`() {
        val message = query("pubads", "g", "doubleclick", "net")
        assertEquals("pubads.g.doubleclick.net", DnsQuestion.hostName(message))
    }

    @Test
    fun `ignores messages without a question`() {
        assertNull(DnsQuestion.hostName(ByteArray(12)))
    }

    private fun query(vararg labels: String): ByteArray {
        val header = ByteArray(12)
        header[5] = 1 // One question.
        val body = labels.fold(ByteArray(0)) { accumulated, label ->
            accumulated + byteArrayOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII)
        }
        return header + body + byteArrayOf(0, 0, 1, 0, 1)
    }
}

class IpV4UdpPacketTest {

    @Test
    fun `parses a udp datagram and builds a reversed response`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val request = datagram(
            source = byteArrayOf(10, 111, 222.toByte(), 2),
            destination = byteArrayOf(10, 111, 222.toByte(), 3),
            sourcePort = 40_000,
            destinationPort = 53,
            payload = payload
        )

        val parsed = requireNotNull(IpV4UdpPacket.parse(request))
        assertEquals(53, parsed.destinationPort)
        assertEquals(40_000, parsed.sourcePort)
        assertTrue(payload.contentEquals(parsed.payload))

        val response = IpV4UdpPacket.buildResponse(parsed, byteArrayOf(9, 9))
        val parsedResponse = requireNotNull(IpV4UdpPacket.parse(response))
        assertEquals(40_000, parsedResponse.destinationPort)
        assertEquals(53, parsedResponse.sourcePort)
        assertTrue(byteArrayOf(10, 111, 222.toByte(), 3).contentEquals(parsedResponse.sourceAddress))
        assertTrue(byteArrayOf(9, 9).contentEquals(parsedResponse.payload))
    }

    private fun datagram(
        source: ByteArray,
        destination: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray
    ): ByteArray {
        val total = 20 + 8 + payload.size
        val packet = ByteArray(total)
        packet[0] = 0x45
        packet[2] = ((total shr 8) and 0xFF).toByte()
        packet[3] = (total and 0xFF).toByte()
        packet[9] = 17
        source.copyInto(packet, 12)
        destination.copyInto(packet, 16)
        packet[20] = ((sourcePort shr 8) and 0xFF).toByte()
        packet[21] = (sourcePort and 0xFF).toByte()
        packet[22] = ((destinationPort shr 8) and 0xFF).toByte()
        packet[23] = (destinationPort and 0xFF).toByte()
        val udpLength = 8 + payload.size
        packet[24] = ((udpLength shr 8) and 0xFF).toByte()
        packet[25] = (udpLength and 0xFF).toByte()
        payload.copyInto(packet, 28)
        return packet
    }
}
