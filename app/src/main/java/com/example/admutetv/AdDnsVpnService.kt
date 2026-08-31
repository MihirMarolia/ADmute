package com.example.admutetv

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Optional second detection source.
 *
 * The service publishes a private DNS address inside a local tunnel and routes
 * only that single address through the tunnel, so ordinary media traffic never
 * passes through this process. Each DNS question is inspected for known ad
 * delivery hostnames, forwarded unchanged to the upstream resolver, and the
 * answer is written back. Nothing is blocked; hostnames are only used as
 * evidence that an ad break is starting in whichever app is in the foreground.
 */
class AdDnsVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val forwarders = Executors.newFixedThreadPool(4)

    @Volatile
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            teardown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_STICKY

        val descriptor = Builder()
            .setSession("AdMute TV ad-domain watch")
            .addAddress(TUNNEL_CLIENT_ADDRESS, 32)
            .addDnsServer(TUNNEL_DNS_ADDRESS)
            .addRoute(TUNNEL_DNS_ADDRESS, 32)
            .setBlocking(true)
            .setMtu(MTU)
            .establish()

        if (descriptor == null) {
            DiagnosticsLog.add("network: could not establish the DNS watch tunnel")
            stopSelf()
            return START_NOT_STICKY
        }

        tunnel = descriptor
        running = true
        AdSignals.networkDetectorRunning = true
        DiagnosticsLog.add("network: DNS ad-domain watch started")

        worker = Thread({ pumpPackets(descriptor) }, "admute-dns").apply { start() }
        return START_STICKY
    }

    override fun onRevoke() {
        teardown()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardown()
        forwarders.shutdownNow()
        super.onDestroy()
    }

    private fun teardown() {
        running = false
        AdSignals.networkDetectorRunning = false
        AdSignals.clearNetworkSignal()
        worker?.interrupt()
        worker = null
        runCatching { tunnel?.close() }
        tunnel = null
        DiagnosticsLog.add("network: DNS ad-domain watch stopped")
    }

    private fun pumpPackets(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(MTU)

        while (running && !Thread.currentThread().isInterrupted) {
            val length = runCatching { input.read(buffer) }.getOrElse { -1 }
            if (length <= 0) continue

            val packet = buffer.copyOf(length)
            val query = IpV4UdpPacket.parse(packet) ?: continue
            if (query.destinationPort != DNS_PORT) continue

            inspectQuestion(query.payload)
            forwarders.execute { forward(query, output) }
        }
    }

    private fun inspectQuestion(payload: ByteArray) {
        val host = DnsQuestion.hostName(payload) ?: return
        if (AdHosts.isAdHost(host)) {
            AdSignals.reportNetworkAdHost(host)
            DiagnosticsLog.add("network: ad host $host")
        }
    }

    private fun forward(query: IpV4UdpPacket, output: FileOutputStream) {
        val socket = DatagramSocket()
        try {
            // Keep the forwarded lookup outside of this tunnel to avoid a loop.
            protect(socket)
            socket.soTimeout = UPSTREAM_TIMEOUT_MS

            val upstream = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(query.payload, query.payload.size, upstream, DNS_PORT))

            val answer = ByteArray(MTU)
            val response = DatagramPacket(answer, answer.size)
            socket.receive(response)

            val reply = IpV4UdpPacket.buildResponse(query, answer.copyOf(response.length))
            synchronized(output) { output.write(reply) }
        } catch (_: Exception) {
            // A dropped lookup is retried by the querying application itself.
        } finally {
            socket.close()
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.admutetv.STOP_DNS_WATCH"

        private const val TUNNEL_CLIENT_ADDRESS = "10.111.222.2"
        private const val TUNNEL_DNS_ADDRESS = "10.111.222.3"
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val DNS_PORT = 53
        private const val MTU = 1500
        private const val UPSTREAM_TIMEOUT_MS = 3_000
    }
}

/** Minimal IPv4/UDP reader and writer for the DNS-only tunnel. */
internal class IpV4UdpPacket private constructor(
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray
) {
    companion object {
        private const val IPV4_VERSION = 4
        private const val UDP_PROTOCOL = 17
        private const val UDP_HEADER_LENGTH = 8

        fun parse(packet: ByteArray): IpV4UdpPacket? {
            if (packet.size < 28) return null
            val versionAndHeaderLength = packet[0].toInt() and 0xFF
            if ((versionAndHeaderLength shr 4) != IPV4_VERSION) return null

            val headerLength = (versionAndHeaderLength and 0x0F) * 4
            if (packet.size < headerLength + UDP_HEADER_LENGTH) return null
            if ((packet[9].toInt() and 0xFF) != UDP_PROTOCOL) return null

            val sourcePort = readUnsignedShort(packet, headerLength)
            val destinationPort = readUnsignedShort(packet, headerLength + 2)
            val udpLength = readUnsignedShort(packet, headerLength + 4)
            val payloadLength = (udpLength - UDP_HEADER_LENGTH)
                .coerceIn(0, packet.size - headerLength - UDP_HEADER_LENGTH)

            val payloadStart = headerLength + UDP_HEADER_LENGTH
            return IpV4UdpPacket(
                sourceAddress = packet.copyOfRange(12, 16),
                destinationAddress = packet.copyOfRange(16, 20),
                sourcePort = sourcePort,
                destinationPort = destinationPort,
                payload = packet.copyOfRange(payloadStart, payloadStart + payloadLength)
            )
        }

        /** Builds the answer packet by reversing the addresses of [query]. */
        fun buildResponse(query: IpV4UdpPacket, payload: ByteArray): ByteArray {
            val totalLength = 20 + UDP_HEADER_LENGTH + payload.size
            val buffer = ByteBuffer.allocate(totalLength)

            buffer.put((IPV4_VERSION shl 4 or 5).toByte())
            buffer.put(0)
            buffer.putShort(totalLength.toShort())
            buffer.putShort(0)
            buffer.putShort(0x4000.toShort()) // Do not fragment.
            buffer.put(64) // TTL
            buffer.put(UDP_PROTOCOL.toByte())
            buffer.putShort(0) // Checksum placeholder.
            buffer.put(query.destinationAddress)
            buffer.put(query.sourceAddress)

            buffer.putShort(query.destinationPort.toShort())
            buffer.putShort(query.sourcePort.toShort())
            buffer.putShort((UDP_HEADER_LENGTH + payload.size).toShort())
            buffer.putShort(0) // A zero UDP checksum is permitted over IPv4.
            buffer.put(payload)

            val packet = buffer.array()
            writeShort(packet, 10, checksum(packet, 0, 20))
            return packet
        }

        private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
            var sum = 0
            var index = offset
            while (index < offset + length - 1) {
                sum += readUnsignedShort(data, index)
                index += 2
            }
            if (index < offset + length) sum += (data[index].toInt() and 0xFF) shl 8
            while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
            return sum.inv() and 0xFFFF
        }

        private fun readUnsignedShort(data: ByteArray, offset: Int): Int =
            ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

        private fun writeShort(data: ByteArray, offset: Int, value: Int) {
            data[offset] = ((value shr 8) and 0xFF).toByte()
            data[offset + 1] = (value and 0xFF).toByte()
        }
    }
}

/** Reads the first question name out of a DNS query message. */
internal object DnsQuestion {
    fun hostName(message: ByteArray): String? {
        if (message.size < 13) return null
        val questionCount = ((message[4].toInt() and 0xFF) shl 8) or (message[5].toInt() and 0xFF)
        if (questionCount < 1) return null

        val labels = StringBuilder()
        var index = 12
        while (index < message.size) {
            val length = message[index].toInt() and 0xFF
            if (length == 0) break
            if (length and 0xC0 != 0) return null // Compression pointers are not used in questions.
            if (index + 1 + length > message.size) return null

            if (labels.isNotEmpty()) labels.append('.')
            labels.append(String(message, index + 1, length, Charsets.US_ASCII))
            index += 1 + length
        }

        val host = labels.toString().lowercase(Locale.ROOT)
        return host.ifBlank { null }
    }
}
