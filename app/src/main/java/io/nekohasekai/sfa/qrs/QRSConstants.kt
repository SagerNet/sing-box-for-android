package io.nekohasekai.sfa.qrs

object QRSConstants {
    const val OFFICIAL_URL_PREFIX = "https://qrss.netlify.app/#"

    const val DEFAULT_FRAME_COUNT = 200
    const val BITMAP_BUFFER_SIZE = 30
    const val RECOVERY_FACTOR = 1.3

    // FPS settings
    const val DEFAULT_FPS = 10
    const val MIN_FPS = 1
    const val MAX_FPS = 60

    // Slice Size settings
    const val DEFAULT_SLICE_SIZE = 512
    const val MIN_SLICE_SIZE = 100
    const val MAX_SLICE_SIZE = 1500

    fun calculateRequiredFrames(dataSize: Int, sliceSize: Int): Int {
        val k = (dataSize + sliceSize - 1) / sliceSize
        if (k == 0) return 1
        return (k * RECOVERY_FACTOR).toInt().coerceAtLeast(k + 5)
    }
}
