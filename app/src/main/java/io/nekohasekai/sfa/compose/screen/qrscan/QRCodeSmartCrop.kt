package io.nekohasekai.sfa.compose.screen.qrscan

import kotlin.math.max
import kotlin.math.min

data class QRCodeCropArea(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
)

object QRCodeSmartCrop {
    fun findCropArea(
        yData: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ): QRCodeCropArea? {
        val minDim = min(width, height)
        if (minDim <= 0) return null

        val step = (minDim / 120).coerceIn(4, 16)
        val samplesWide = (width + step - 1) / step
        val samplesHigh = (height + step - 1) / step
        val sampleCount = samplesWide * samplesHigh
        if (sampleCount == 0) return null

        val histogram = IntArray(256)
        var sum = 0
        var maxLuma = 0
        var sy = 0
        var y = 0
        while (sy < samplesHigh) {
            val rowOffset = y * width
            var sx = 0
            var x = 0
            while (sx < samplesWide) {
                val value = yData[rowOffset + x].toInt() and 0xFF
                sum += value
                histogram[value]++
                if (value > maxLuma) maxLuma = value
                sx++
                x += step
            }
            sy++
            y += step
        }

        val mean = sum / sampleCount
        val contrast = maxLuma - mean
        if (contrast < 30) return null

        val p95 = percentile(histogram, sampleCount, 0.95f)
        val p90 = percentile(histogram, sampleCount, 0.90f)
        val p85 = percentile(histogram, sampleCount, 0.85f)

        val thresholds = intArrayOf(
            max((mean + contrast * 0.75f).toInt(), p95),
            max((mean + contrast * 0.6f).toInt(), p90),
            max((mean + contrast * 0.5f).toInt(), p85),
        )

        val minBrightSamples = max(12, sampleCount / 300)
        var bestArea: QRCodeCropArea? = null
        var bestRatio = 1f

        for (i in thresholds.indices) {
            val threshold = thresholds[i].coerceAtMost(250)
            val component = findBestComponent(
                yData,
                width,
                height,
                step,
                samplesWide,
                samplesHigh,
                threshold,
                minBrightSamples,
            ) ?: continue

            val area = buildCropArea(component, step, width, height, rotationDegrees) ?: continue
            val areaRatio = ((area.right - area.left) * (area.bottom - area.top)).toFloat() / (width * height)
            val maxRatio = if (i == thresholds.lastIndex) 0.9f else 0.82f
            if (areaRatio <= maxRatio) return area
            if (areaRatio < bestRatio) {
                bestRatio = areaRatio
                bestArea = area
            }
        }

        return bestArea
    }

    private data class CropComponent(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
        val count: Int,
        val score: Float,
    )

    private fun findBestComponent(
        yData: ByteArray,
        width: Int,
        height: Int,
        step: Int,
        samplesWide: Int,
        samplesHigh: Int,
        threshold: Int,
        minBrightSamples: Int,
    ): CropComponent? {
        val totalSamples = samplesWide * samplesHigh
        val bright = BooleanArray(totalSamples)
        var brightCount = 0

        var sy = 0
        var y = 0
        while (sy < samplesHigh) {
            val rowOffset = y * width
            var sx = 0
            var x = 0
            while (sx < samplesWide) {
                val value = yData[rowOffset + x].toInt() and 0xFF
                if (value >= threshold) {
                    bright[sy * samplesWide + sx] = true
                    brightCount++
                }
                sx++
                x += step
            }
            sy++
            y += step
        }

        if (brightCount < minBrightSamples) return null

        val visited = BooleanArray(totalSamples)
        val queue = IntArray(totalSamples)
        val minComponentSamples = max(8, minBrightSamples / 3)
        val centerX = width / 2f
        val centerY = height / 2f
        val maxDistSq = centerX * centerX + centerY * centerY

        var best: CropComponent? = null
        for (cy in 0 until samplesHigh) {
            for (cx in 0 until samplesWide) {
                val index = cy * samplesWide + cx
                if (!bright[index] || visited[index]) continue

                var head = 0
                var tail = 0
                queue[tail++] = index
                visited[index] = true

                var count = 0
                var minX = cx
                var maxX = cx
                var minY = cy
                var maxY = cy

                while (head < tail) {
                    val current = queue[head++]
                    val x = current % samplesWide
                    val yIndex = current / samplesWide
                    count++

                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (yIndex < minY) minY = yIndex
                    if (yIndex > maxY) maxY = yIndex

                    val startX = if (x > 0) x - 1 else x
                    val endX = if (x + 1 < samplesWide) x + 1 else x
                    val startY = if (yIndex > 0) yIndex - 1 else yIndex
                    val endY = if (yIndex + 1 < samplesHigh) yIndex + 1 else yIndex

                    var ny = startY
                    while (ny <= endY) {
                        val rowIndex = ny * samplesWide
                        var nx = startX
                        while (nx <= endX) {
                            if (nx != x || ny != yIndex) {
                                val neighbor = rowIndex + nx
                                if (bright[neighbor] && !visited[neighbor]) {
                                    visited[neighbor] = true
                                    queue[tail++] = neighbor
                                }
                            }
                            nx++
                        }
                        ny++
                    }
                }

                if (count < minComponentSamples) continue

                val compWidth = maxX - minX + 1
                val compHeight = maxY - minY + 1
                val aspect = max(compWidth.toFloat() / compHeight, compHeight.toFloat() / compWidth)
                val aspectPenalty = ((aspect - 1f) / 1.6f).coerceIn(0f, 1f)
                val compCenterX = (minX + maxX + 1) * 0.5f * step
                val compCenterY = (minY + maxY + 1) * 0.5f * step
                val dx = compCenterX - centerX
                val dy = compCenterY - centerY
                val normDist = if (maxDistSq > 0f) (dx * dx + dy * dy) / maxDistSq else 0f
                val edgeTouches = (if (minX == 0) 1 else 0) +
                    (if (minY == 0) 1 else 0) +
                    (if (maxX == samplesWide - 1) 1 else 0) +
                    (if (maxY == samplesHigh - 1) 1 else 0)

                var score = count.toFloat()
                score *= 1f - 0.5f * normDist.coerceIn(0f, 1f)
                score *= 1f - 0.35f * aspectPenalty
                score *= 1f - 0.15f * edgeTouches

                if (best == null || score > best!!.score) {
                    best = CropComponent(
                        minX = minX,
                        minY = minY,
                        maxX = maxX,
                        maxY = maxY,
                        count = count,
                        score = score,
                    )
                }
            }
        }

        return best
    }

    private fun buildCropArea(
        component: CropComponent,
        step: Int,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ): QRCodeCropArea? {
        val left = component.minX * step
        val top = component.minY * step
        val right = min(width, (component.maxX + 1) * step)
        val bottom = min(height, (component.maxY + 1) * step)
        val cropWidth = right - left
        val cropHeight = bottom - top
        if (cropWidth <= 0 || cropHeight <= 0) return null

        val frameArea = width * height
        val cropArea = cropWidth * cropHeight
        if (cropArea < frameArea / 96) return null

        val aspect = cropWidth.toFloat() / cropHeight
        if (aspect < 0.45f || aspect > 2.2f) return null

        val padding = (max(cropWidth, cropHeight) * 0.14f).toInt().coerceAtLeast(step * 2)
        val cropLeft = (left - padding).coerceAtLeast(0)
        val cropTop = (top - padding).coerceAtLeast(0)
        val cropRight = (right + padding).coerceAtMost(width)
        val cropBottom = (bottom + padding).coerceAtMost(height)
        if (cropRight <= cropLeft || cropBottom <= cropTop) return null

        return QRCodeCropArea(
            left = cropLeft,
            top = cropTop,
            right = cropRight,
            bottom = cropBottom,
            imageWidth = width,
            imageHeight = height,
            rotationDegrees = rotationDegrees,
        )
    }

    private fun percentile(histogram: IntArray, count: Int, percentile: Float): Int {
        if (count <= 0) return 0
        val target = (count * percentile).toInt().coerceIn(0, count - 1)
        var acc = 0
        for (i in histogram.indices) {
            acc += histogram[i]
            if (acc > target) return i
        }
        return histogram.lastIndex
    }
}
