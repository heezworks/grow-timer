package app.sn62kr.grow.core.garden

import app.sn62kr.grow.core.draw.Rng

/** 화분 식물 넷(D56의 지중해 여름. 말로는 하지 않고 그림으로만). */
enum class Species(val key: String) { OLIVE("olive"), LEMON("lemon"), LAVENDER("lavender"), GERANIUM("geranium") }

/** 식물 하나의 지금 모습. [growth]는 그림에 넘기는 0~1, [stage]는 지난 단계 경계의 수 0~5(5 = 다 자람). */
data class PlantState(
    val order: Int,
    val species: Species,
    val seed: Long,
    val fullSec: Long,
    val spentSec: Long,
    val growth: Float,
    val stage: Int,
    /** 다음 단계까지 남은 초. 다 자랐으면 0. */
    val toNextStageSec: Long,
) {
    val done get() = spentSec >= fullSec
}

data class GardenState(val current: PlantState, val grown: List<PlantState>, val stillLife: Int, val toNextStillLifeSec: Long)

/**
 * 식물은 지금 키우는 하나에 모든 활동의 시간이 모인다(Q15). 쌓인 시간 전체에서 계산하고 저장하지 않는다(design.md 5절).
 * 식물은 죽지 않는다 — 쉬면 그 모습 그대로 기다린다(D23).
 */
object Garden {
    private const val H = 3600L

    /** k번째 식물(0부터)이 다 자라는 시간(Q14): 10, 25, 50, 그다음은 100시간. */
    fun fullSec(k: Int): Long = when (k) { 0 -> 10 * H; 1 -> 25 * H; 2 -> 50 * H; else -> 100 * H }

    /** 단계 경계 = 다 자라는 시간에 대한 비율. 첫 식물이면 30분·2시간·4시간·7시간·10시간(Q14). */
    val STAGES = floatArrayOf(0.05f, 0.2f, 0.4f, 0.7f, 1f)

    /**
     * 시간 비율 → 그림의 성장. 단계마다 0.2씩이라 처음이 빨리 자란다(하루 30분이면 첫 4주에 눈에 띄는 변화 4번).
     * 단계 사이에도 이어서 자란다 — 부품이 하나씩 돋는다(D66).
     */
    fun visual(f: Float): Float {
        if (f <= 0f) return 0f
        if (f >= 1f) return 1f
        var lo = 0f
        for (i in STAGES.indices) {
            val hi = STAGES[i]
            if (f <= hi) return (i + (f - lo) / (hi - lo)) / STAGES.size
            lo = hi
        }
        return 1f
    }

    /**
     * 첫 식물은 올리브, 2~4번째는 나머지 셋을 섞은 차례(넷이 다 한 번씩 나온다).
     * 그 뒤로는 넷씩 섞되 바로 앞과 같은 종은 피한다. 같은 정원 씨앗이면 언제나 같은 순서.
     */
    fun species(gardenSeed: Long, k: Int): Species {
        if (k == 0) return Species.OLIVE
        if (k <= 3) {
            val rest = listOf(Species.LEMON, Species.LAVENDER, Species.GERANIUM)
            return rest[Rng(gardenSeed).shuffled(3)[k - 1]]
        }
        val block = (k - 4) / 4
        val order = Rng(gardenSeed xor ((block + 1) * 0x9E3779B97F4A7C15uL.toLong())).shuffled(4).map { Species.entries[it] }.toMutableList()
        val prev = species(gardenSeed, 4 + block * 4 - 1)
        if (order[0] == prev) { order[0] = order[1]; order[1] = prev } // 앞 묶음의 끝과 겹치지 않게
        return order[(k - 4) % 4]
    }

    fun plantSeed(gardenSeed: Long, k: Int): Long = Rng(gardenSeed + k * 0x2545F4914F6CDD1DL).nextLong()

    fun of(totalSec: Long, gardenSeed: Long): GardenState {
        val grown = ArrayList<PlantState>()
        var left = totalSec.coerceAtLeast(0)
        var k = 0
        while (left >= fullSec(k)) {
            grown += state(k, gardenSeed, fullSec(k))
            left -= fullSec(k); k++
        }
        val (n, next) = stillLife(totalSec)
        return GardenState(state(k, gardenSeed, left), grown, n, next)
    }

    private fun state(k: Int, gardenSeed: Long, spent: Long): PlantState {
        val full = fullSec(k)
        val f = spent.toFloat() / full
        val stage = STAGES.count { f >= it }
        val nextAt = STAGES.firstOrNull { f < it }?.let { (it * full).toLong() }
        return PlantState(k, species(gardenSeed, k), plantSeed(gardenSeed, k), full, spent, visual(f),
            stage, nextAt?.let { (it - spent).coerceAtLeast(1) } ?: 0)
    }

    /**
     * 정물은 누적 1·3·6·10·15·21·28…시간(간격이 1시간씩 는다 = 삼각수)에 하나씩 저절로 놓인다(design.md 2절, D57).
     * 상점·화폐·고르기 없음. 돌려주는 값: (놓인 수, 다음까지 남은 초).
     */
    fun stillLife(totalSec: Long): Pair<Int, Long> {
        var n = 0
        while ((n + 1L) * (n + 2) / 2 * H <= totalSec) n++
        val next = (n + 1L) * (n + 2) / 2 * H
        return n to (next - totalSec)
    }
}
