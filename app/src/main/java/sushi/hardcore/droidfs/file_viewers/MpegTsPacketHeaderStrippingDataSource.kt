package sushi.hardcore.droidfs.file_viewers

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlin.math.min

@OptIn(UnstableApi::class)
class MpegTsPacketHeaderStrippingDataSource(
    private val upstreamFactory: DataSource.Factory
) : DataSource {
    companion object {
        private const val TS_PACKET_SIZE = 188
        private const val PACKETIZED_TS_PACKET_SIZE = 192
        private const val PACKET_HEADER_SIZE = PACKETIZED_TS_PACKET_SIZE - TS_PACKET_SIZE
        private const val TS_SYNC_BYTE = 0x47.toByte()
        private const val PACKETS_TO_SNIFF = 3
    }

    private val transferListeners = mutableListOf<TransferListener>()
    private val scratch = ByteArray(PACKET_HEADER_SIZE)

    private var upstream: DataSource? = null
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var payloadOffset = 0
    private var stripPacketHeaders = false

    override fun open(dataSpec: DataSpec): Long {
        close()

        val layout = sniffLayout(dataSpec.uri)
        stripPacketHeaders = layout.hasPacketHeaders

        val source = upstreamFactory.createDataSource()
        transferListeners.forEach(source::addTransferListener)
        upstream = source

        if (!stripPacketHeaders) {
            bytesRemaining = source.open(dataSpec)
            return bytesRemaining
        }

        val virtualLength = layout.virtualLength()
        val availableBytes = if (virtualLength == C.LENGTH_UNSET.toLong()) {
            C.LENGTH_UNSET.toLong()
        } else {
            (virtualLength - dataSpec.position).coerceAtLeast(0)
        }
        bytesRemaining = when {
            dataSpec.length == C.LENGTH_UNSET.toLong() -> availableBytes
            availableBytes == C.LENGTH_UNSET.toLong() -> dataSpec.length
            else -> min(availableBytes, dataSpec.length)
        }

        if (bytesRemaining == 0L) {
            return 0
        }

        payloadOffset = (dataSpec.position % TS_PACKET_SIZE).toInt()
        source.open(dataSpec.physicalSubrange(dataSpec.position, bytesRemaining))
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) {
            return 0
        }

        val source = upstream ?: return C.RESULT_END_OF_INPUT
        if (!stripPacketHeaders) {
            return source.read(buffer, offset, readLength)
        }

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            readLength
        } else {
            min(readLength.toLong(), bytesRemaining).toInt()
        }
        if (bytesToRead == 0) {
            return C.RESULT_END_OF_INPUT
        }

        var totalRead = 0
        while (totalRead < bytesToRead) {
            if (payloadOffset == TS_PACKET_SIZE) {
                if (!skipPacketHeader(source)) {
                    break
                }
                payloadOffset = 0
            }

            val bytesInPacket = min(TS_PACKET_SIZE - payloadOffset, bytesToRead - totalRead)
            val read = source.read(buffer, offset + totalRead, bytesInPacket)
            if (read == C.RESULT_END_OF_INPUT) {
                break
            }
            if (read == 0) {
                break
            }

            totalRead += read
            payloadOffset += read
        }

        if (totalRead == 0) {
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= totalRead
        }
        return totalRead
    }

    override fun getUri(): Uri? {
        return upstream?.uri
    }

    override fun close() {
        upstream?.close()
        upstream = null
        bytesRemaining = C.LENGTH_UNSET.toLong()
        payloadOffset = 0
        stripPacketHeaders = false
    }

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        upstream?.addTransferListener(transferListener)
    }

    private fun sniffLayout(uri: Uri): SourceLayout {
        val source = upstreamFactory.createDataSource()
        transferListeners.forEach(source::addTransferListener)
        return try {
            val sourceLength = source.open(DataSpec(uri, 0, C.LENGTH_UNSET.toLong()))
            val probe = ByteArray(PACKETIZED_TS_PACKET_SIZE * PACKETS_TO_SNIFF)
            val bytesRead = readProbe(source, probe)
            SourceLayout(
                physicalLength = sourceLength,
                hasPacketHeaders = hasPacketizedTransportStreamLayout(probe, bytesRead)
            )
        } finally {
            source.close()
        }
    }

    private fun readProbe(source: DataSource, probe: ByteArray): Int {
        var totalRead = 0
        while (totalRead < probe.size) {
            val read = source.read(probe, totalRead, probe.size - totalRead)
            if (read == C.RESULT_END_OF_INPUT) {
                break
            }
            if (read == 0) {
                break
            }
            totalRead += read
        }
        return totalRead
    }

    private fun hasPacketizedTransportStreamLayout(probe: ByteArray, bytesRead: Int): Boolean {
        if (bytesRead < PACKETIZED_TS_PACKET_SIZE * PACKETS_TO_SNIFF) {
            return false
        }

        var packetizedSyncBytes = 0
        var plainSyncBytes = 0
        repeat(PACKETS_TO_SNIFF) { packetIndex ->
            if (probe[packetIndex * PACKETIZED_TS_PACKET_SIZE + PACKET_HEADER_SIZE] == TS_SYNC_BYTE) {
                packetizedSyncBytes++
            }
            if (probe[packetIndex * TS_PACKET_SIZE] == TS_SYNC_BYTE) {
                plainSyncBytes++
            }
        }
        return packetizedSyncBytes == PACKETS_TO_SNIFF && packetizedSyncBytes > plainSyncBytes
    }

    private fun skipPacketHeader(source: DataSource): Boolean {
        var skipped = 0
        while (skipped < PACKET_HEADER_SIZE) {
            val read = source.read(scratch, skipped, PACKET_HEADER_SIZE - skipped)
            if (read <= 0) {
                return false
            }
            skipped += read
        }
        return true
    }

    private fun DataSpec.physicalSubrange(virtualPosition: Long, virtualLength: Long): DataSpec {
        val physicalPosition = virtualToPhysicalOffset(virtualPosition)
        val physicalLength = if (virtualLength == C.LENGTH_UNSET.toLong()) {
            C.LENGTH_UNSET.toLong()
        } else {
            virtualToPhysicalOffset(virtualPosition + virtualLength - 1) + 1 - physicalPosition
        }
        return buildUpon()
            .setPosition(physicalPosition)
            .setLength(physicalLength)
            .build()
    }

    private fun virtualToPhysicalOffset(virtualOffset: Long): Long {
        return virtualOffset / TS_PACKET_SIZE * PACKETIZED_TS_PACKET_SIZE +
                PACKET_HEADER_SIZE +
                virtualOffset % TS_PACKET_SIZE
    }

    private data class SourceLayout(
        val physicalLength: Long,
        val hasPacketHeaders: Boolean
    ) {
        fun virtualLength(): Long {
            return if (physicalLength == C.LENGTH_UNSET.toLong()) {
                C.LENGTH_UNSET.toLong()
            } else {
                physicalLength / PACKETIZED_TS_PACKET_SIZE * TS_PACKET_SIZE
            }
        }
    }
}
