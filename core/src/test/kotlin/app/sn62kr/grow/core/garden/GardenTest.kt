package app.sn62kr.grow.core.garden

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GardenTest {
    private val h = 3600L
    private val seed = 42L

    @Test fun firstPlantStages() { // Q14: 30분·2시간·4시간·7시간·10시간
        assertEquals(0, Garden.of(0, seed).current.stage)
        assertEquals(0, Garden.of(29 * 60, seed).current.stage)
        assertEquals(1, Garden.of(30 * 60, seed).current.stage)
        assertEquals(2, Garden.of(2 * h, seed).current.stage)
        assertEquals(3, Garden.of(4 * h, seed).current.stage)
        assertEquals(4, Garden.of(7 * h, seed).current.stage)
        assertEquals(90 * 60L, Garden.of(30 * 60, seed).current.toNextStageSec)
        assertEquals(Species.OLIVE, Garden.of(0, seed).current.species)
    }

    @Test fun plantsFollowEachOther() {
        val g = Garden.of(10 * h, seed) // 첫 식물이 막 다 자랐다
        assertEquals(1, g.grown.size); assertEquals(1, g.current.order); assertEquals(0L, g.current.spentSec)
        val g2 = Garden.of(10 * h + 25 * h + 50 * h + 150 * h, seed) // 10 + 25 + 50 + 100 + 50
        assertEquals(4, g2.grown.size); assertEquals(50 * h, g2.current.spentSec); assertEquals(100 * h, g2.current.fullSec)
    }

    @Test fun visualIsMonotoneAndHitsStageMarks() {
        var prev = -1f
        for (i in 0..1000) { val v = Garden.visual(i / 1000f); assertTrue(v >= prev); prev = v }
        assertEquals(0.2f, Garden.visual(0.05f), 1e-5f); assertEquals(0.8f, Garden.visual(0.7f), 1e-5f); assertEquals(1f, Garden.visual(1f))
    }

    @Test fun stillLifeTriangular() { // 1·3·6·10·15·21시간
        assertEquals(0 to h, Garden.stillLife(0))
        assertEquals(1 to 2 * h, Garden.stillLife(h))
        assertEquals(3, Garden.stillLife(6 * h).first)
        assertEquals(5, Garden.stillLife(20 * h).first)
        assertEquals(6, Garden.stillLife(21 * h).first)
    }

    @Test fun speciesOrderIsStable() {
        val a = (0 until 12).map { Garden.species(seed, it) }
        assertEquals(a, (0 until 12).map { Garden.species(seed, it) })
        assertEquals(setOf(Species.LEMON, Species.LAVENDER, Species.GERANIUM), a.subList(1, 4).toSet()) // 2~4번째는 나머지 셋
        for (seed in 0L until 50L) { val l = (0 until 30).map { Garden.species(seed, it) }; assertTrue(l.zipWithNext().none { (x, y) -> x == y }) } // 같은 종이 잇달지 않는다
    }
}
