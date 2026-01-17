package io.nekohasekai.sfa.qrs

import java.util.zip.CRC32
import kotlin.random.Random

class LubyCodec(private val sliceSize: Int = QRSConstants.DEFAULT_SLICE_SIZE) {
    internal class IntArrayKey(val indices: IntArray) {
        private val hash = indices.contentHashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IntArrayKey) return false
            return indices.contentEquals(other.indices)
        }

        override fun hashCode(): Int = hash
    }

    data class EncodedBlock(
        val degree: Int,
        val indices: IntArray,
        val totalBlocks: Int,
        val compressedSize: Int,
        val checksum: Long,
        val data: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncodedBlock
            return degree == other.degree &&
                indices.contentEquals(other.indices) &&
                totalBlocks == other.totalBlocks &&
                compressedSize == other.compressedSize &&
                checksum == other.checksum &&
                data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = degree
            result = 31 * result + indices.contentHashCode()
            result = 31 * result + totalBlocks
            result = 31 * result + compressedSize
            result = 31 * result + checksum.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    class DecodingState(val totalBlocks: Int, val compressedSize: Int, val checksum: Long) {
        val decodedBlocks: Array<ByteArray?> = arrayOfNulls(totalBlocks)
        var decodedCount: Int = 0

        internal val blockKeyMap: MutableMap<IntArrayKey, PendingBlock> = mutableMapOf()
        internal val blockSubkeyMap: MutableMap<IntArrayKey, MutableSet<PendingBlock>> = mutableMapOf()
        val blockIndexMap: MutableMap<Int, MutableSet<PendingBlock>> = mutableMapOf()
        val blockDisposeMap: MutableMap<Int, MutableList<() -> Unit>> = mutableMapOf()

        class PendingBlock(var indices: MutableList<Int>, var data: ByteArray)
    }

    fun encode(originalData: ByteArray, compressedData: ByteArray, compressedSize: Int): Sequence<EncodedBlock> = sequence {
        val k = (compressedData.size + sliceSize - 1) / sliceSize
        if (k == 0) return@sequence

        val paddedData = compressedData.copyOf(k * sliceSize)
        val blocks = (0 until k).map { i ->
            paddedData.copyOfRange(i * sliceSize, (i + 1) * sliceSize)
        }

        val crc = CRC32()
        crc.update(originalData)
        // Official: (raw_crc ^ k ^ 0xFFFFFFFF)
        // Java CRC32.getValue() = raw_crc ^ 0xFFFFFFFF
        // So: official = getValue() ^ 0xFFFFFFFF ^ k ^ 0xFFFFFFFF = getValue() ^ k
        val checksum = (crc.value xor k.toLong()) and 0xFFFFFFFFL

        var seed = 0L
        while (true) {
            val random = Random(seed++)
            val degree = SolitonDistribution.sample(k, random)
            val indices = selectIndices(k, degree, random)
            val blockData = xorBlocks(blocks, indices)

            yield(
                EncodedBlock(
                    degree = degree,
                    indices = indices,
                    totalBlocks = k,
                    compressedSize = compressedSize,
                    checksum = checksum,
                    data = blockData,
                ),
            )
        }
    }

    fun createDecodingState(firstBlock: EncodedBlock): DecodingState = DecodingState(
        totalBlocks = firstBlock.totalBlocks,
        compressedSize = firstBlock.compressedSize,
        checksum = firstBlock.checksum,
    )

    fun processBlock(state: DecodingState, block: EncodedBlock): Boolean {
        val queue = ArrayDeque<DecodingState.PendingBlock>()
        queue.add(DecodingState.PendingBlock(block.indices.sorted().toMutableList(), block.data.clone()))

        while (queue.isNotEmpty()) {
            val pending = queue.removeFirst()
            processPendingBlock(state, pending, queue)
        }

        return state.decodedCount == state.totalBlocks
    }

    private fun processPendingBlock(
        state: DecodingState,
        pending: DecodingState.PendingBlock,
        queue: ArrayDeque<DecodingState.PendingBlock>,
    ) {
        var indices = pending.indices
        val data = pending.data

        val key = indicesToKey(indices)
        if (state.blockKeyMap.containsKey(key) || indices.all { state.decodedBlocks[it] != null }) {
            return
        }

        // XOR with already decoded blocks
        if (indices.size > 1) {
            val toRemove = mutableListOf<Int>()
            for (idx in indices) {
                state.decodedBlocks[idx]?.let {
                    xorInPlace(data, it)
                    toRemove.add(idx)
                }
            }
            if (toRemove.isNotEmpty()) {
                indices.removeAll(toRemove)
            }
        }

        // Try subset lookup: [1,2,3] XOR [1,2] = [3]
        if (indices.size > 2) {
            for (i in indices.indices) {
                val subkey = indicesToKey(indices.filterIndexed { j, _ -> j != i })
                state.blockKeyMap[subkey]?.let { subblock ->
                    xorInPlace(data, subblock.data)
                    indices = mutableListOf(indices[i])
                    pending.indices = indices
                    return@let
                }
            }
        }

        // Still pending: store and register for future matching
        if (indices.size > 1) {
            val newKey = indicesToKey(indices)
            state.blockKeyMap[newKey] = pending

            // Register for single-index lookups
            for (idx in indices) {
                state.blockIndexMap.getOrPut(idx) { mutableSetOf() }.add(pending)
            }

            // Register subkeys for superset matching (degree > 2)
            if (indices.size > 2) {
                for (i in indices.indices) {
                    val subkey = indicesToKey(indices.filterIndexed { j, _ -> j != i })
                    val dispose: () -> Unit = { state.blockSubkeyMap[subkey]?.remove(pending) }
                    state.blockSubkeyMap.getOrPut(subkey) { mutableSetOf() }.add(pending)
                    state.blockDisposeMap.getOrPut(indices[i]) { mutableListOf() }.add(dispose)
                }
            }

            // Check if this block can help decode any supersets
            state.blockSubkeyMap[newKey]?.let { supersets ->
                state.blockSubkeyMap.remove(newKey)
                for (superblock in supersets.toList()) {
                    // Remove old registrations before modifying
                    val oldKey = indicesToKey(superblock.indices)
                    state.blockKeyMap.remove(oldKey)
                    for (idx in superblock.indices) {
                        state.blockIndexMap[idx]?.remove(superblock)
                    }

                    xorInPlace(superblock.data, data)
                    superblock.indices.removeAll(indices)

                    // Re-process through queue
                    queue.add(superblock)
                }
            }
        } else if (indices.size == 1) {
            val idx = indices[0]
            if (state.decodedBlocks[idx] == null) {
                state.decodedBlocks[idx] = data
                state.decodedCount++
                propagateDecoding(state, idx, queue)
            }
        }
    }

    private fun indicesToKey(indices: List<Int>): IntArrayKey = IntArrayKey(indices.sorted().toIntArray())

    private fun propagateDecoding(state: DecodingState, decodedIdx: Int, queue: ArrayDeque<DecodingState.PendingBlock>) {
        val toProcess = ArrayDeque<Int>()
        toProcess.add(decodedIdx)

        while (toProcess.isNotEmpty()) {
            val idx = toProcess.removeFirst()
            val decodedData = state.decodedBlocks[idx] ?: continue

            // Dispose subkey registrations for this index
            state.blockDisposeMap.remove(idx)?.forEach { it() }

            // Find and process blocks containing this index
            val blocks = state.blockIndexMap.remove(idx) ?: continue
            for (pending in blocks) {
                val oldKey = indicesToKey(pending.indices)
                state.blockKeyMap.remove(oldKey)

                xorInPlace(pending.data, decodedData)
                pending.indices.remove(idx)

                // Remove from other index maps
                for (otherIdx in pending.indices) {
                    state.blockIndexMap[otherIdx]?.remove(pending)
                }

                if (pending.indices.size == 1) {
                    val newIdx = pending.indices[0]
                    if (state.decodedBlocks[newIdx] == null) {
                        state.decodedBlocks[newIdx] = pending.data
                        state.decodedCount++
                        toProcess.add(newIdx)
                    }
                } else if (pending.indices.size > 1) {
                    // Re-process through queue to properly update all registrations
                    queue.add(pending)
                }
            }
        }
    }

    fun assembleData(state: DecodingState): ByteArray {
        val result = ByteArray(state.totalBlocks * sliceSize)
        for (i in state.decodedBlocks.indices) {
            state.decodedBlocks[i]?.copyInto(result, i * sliceSize)
        }
        return result
    }

    fun verifyChecksum(originalData: ByteArray, expectedChecksum: Long, k: Int): Boolean {
        val crc = CRC32()
        crc.update(originalData)
        // Official: (raw_crc ^ k ^ 0xFFFFFFFF)
        // Java CRC32.getValue() = raw_crc ^ 0xFFFFFFFF
        // So: official = getValue() ^ 0xFFFFFFFF ^ k ^ 0xFFFFFFFF = getValue() ^ k
        val computed = (crc.value xor k.toLong()) and 0xFFFFFFFFL
        return computed == expectedChecksum
    }

    private fun selectIndices(k: Int, degree: Int, random: Random): IntArray {
        val indices = (0 until k).shuffled(random).take(degree.coerceAtMost(k))
        return indices.toIntArray()
    }

    private fun xorBlocks(blocks: List<ByteArray>, indices: IntArray): ByteArray {
        val result = blocks[indices[0]].clone()
        for (i in 1 until indices.size) {
            xorInPlace(result, blocks[indices[i]])
        }
        return result
    }

    private fun xorInPlace(dest: ByteArray, src: ByteArray) {
        val len = minOf(dest.size, src.size)
        var i = 0

        // Process 8 bytes at a time using Long
        while (i + 7 < len) {
            val destLong = ((dest[i].toLong() and 0xFF) shl 56) or
                ((dest[i + 1].toLong() and 0xFF) shl 48) or
                ((dest[i + 2].toLong() and 0xFF) shl 40) or
                ((dest[i + 3].toLong() and 0xFF) shl 32) or
                ((dest[i + 4].toLong() and 0xFF) shl 24) or
                ((dest[i + 5].toLong() and 0xFF) shl 16) or
                ((dest[i + 6].toLong() and 0xFF) shl 8) or
                (dest[i + 7].toLong() and 0xFF)

            val srcLong = ((src[i].toLong() and 0xFF) shl 56) or
                ((src[i + 1].toLong() and 0xFF) shl 48) or
                ((src[i + 2].toLong() and 0xFF) shl 40) or
                ((src[i + 3].toLong() and 0xFF) shl 32) or
                ((src[i + 4].toLong() and 0xFF) shl 24) or
                ((src[i + 5].toLong() and 0xFF) shl 16) or
                ((src[i + 6].toLong() and 0xFF) shl 8) or
                (src[i + 7].toLong() and 0xFF)

            val result = destLong xor srcLong

            dest[i] = (result shr 56).toByte()
            dest[i + 1] = (result shr 48).toByte()
            dest[i + 2] = (result shr 40).toByte()
            dest[i + 3] = (result shr 32).toByte()
            dest[i + 4] = (result shr 24).toByte()
            dest[i + 5] = (result shr 16).toByte()
            dest[i + 6] = (result shr 8).toByte()
            dest[i + 7] = result.toByte()

            i += 8
        }

        // Process remaining bytes
        while (i < len) {
            dest[i] = (dest[i].toInt() xor src[i].toInt()).toByte()
            i++
        }
    }
}
