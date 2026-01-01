package io.nekohasekai.sfa.qrs

import kotlin.random.Random

object SolitonDistribution {
    fun sample(k: Int, random: Random): Int {
        if (k <= 0) return 1

        val p = random.nextDouble()
        var cdf = 1.0 / k
        if (p < cdf) return 1

        for (d in 2..k) {
            cdf += 1.0 / (d * (d - 1))
            if (p < cdf) return d
        }
        return k
    }
}
