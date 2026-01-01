package io.nekohasekai.sfa.qrs

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class QRSEncoder(
    private val sliceSize: Int = QRSConstants.DEFAULT_SLICE_SIZE,
) {
    private val codec = LubyCodec(sliceSize)

    companion object {
        fun appendFileHeaderMeta(
            data: ByteArray,
            filename: String? = null,
            contentType: String? = null,
        ): ByteArray {
            val meta = buildString {
                append("{")
                var hasContent = false
                filename?.let {
                    append("\"filename\":\"")
                    append(escapeJson(it))
                    append("\"")
                    hasContent = true
                }
                contentType?.let {
                    if (hasContent) append(",")
                    append("\"contentType\":\"")
                    append(escapeJson(it))
                    append("\"")
                }
                append("}")
            }
            val metaBytes = meta.toByteArray(Charsets.ISO_8859_1)

            val result = ByteArray(4 + metaBytes.size + 4 + data.size)
            var offset = 0

            result.writeIntBE(offset, metaBytes.size)
            offset += 4
            metaBytes.copyInto(result, offset)
            offset += metaBytes.size

            result.writeIntBE(offset, data.size)
            offset += 4
            data.copyInto(result, offset)

            return result
        }

        private fun escapeJson(s: String): String {
            return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }
    }

    data class QRSFrame(
        val content: String,
        val frameIndex: Int,
        val totalBlocks: Int,
    )

    fun encode(data: ByteArray, urlPrefix: String = ""): Sequence<QRSFrame> {
        val compressed = compress(data)

        return codec.encode(data, compressed, compressed.size).mapIndexed { index, block ->
            val payload = buildPayload(block)
            val base64 = Base64.getEncoder().encodeToString(payload)
            QRSFrame("$urlPrefix$base64", index, block.totalBlocks)
        }
    }

    private fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()

        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)

        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }

        deflater.end()
        return outputStream.toByteArray()
    }

    private fun buildPayload(block: LubyCodec.EncodedBlock): ByteArray {
        val headerSize = 4 + 4 * block.indices.size + 4 + 4 + 4
        val payload = ByteArray(headerSize + block.data.size)
        var offset = 0

        payload.writeIntLE(offset, block.degree)
        offset += 4

        for (idx in block.indices) {
            payload.writeIntLE(offset, idx)
            offset += 4
        }

        payload.writeIntLE(offset, block.totalBlocks)
        offset += 4

        payload.writeIntLE(offset, block.compressedSize)
        offset += 4

        payload.writeIntLE(offset, block.checksum.toInt())
        offset += 4

        block.data.copyInto(payload, offset)

        return payload
    }

}
