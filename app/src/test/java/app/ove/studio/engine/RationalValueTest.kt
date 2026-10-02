package app.ove.studio.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exact rational arithmetic — the engine boundary never sees floats (P-5). */
class RationalValueTest {

    @Test
    fun add_exact() {
        // 1/3 + 1/6 = 1/2 (never float)
        val r = RationalValue(1, 3).add(RationalValue(1, 6))
        assertEquals(1L, r.num)
        assertEquals(2L, r.den)
    }

    @Test
    fun sub_exact_and_negative() {
        val r = RationalValue(1, 24).sub(RationalValue(1, 12))
        assertEquals(-1L, r.num)
        assertEquals(24L, r.den)
    }

    @Test
    fun normalization_reduces() {
        val r = RationalValue(168, 24) // 7
        assertEquals(7L, r.num)
        assertEquals(1L, r.den)
    }

    @Test
    fun tick_axis_roundtrip_stays_exact() {
        // 1/24 s steps accumulate exactly on a 48 kHz axis
        var t = RationalValue.ZERO
        val frame = RationalValue(1, 24)
        repeat(24) { t = t.add(frame) }
        assertEquals(1L, t.num)
        assertEquals(1L, t.den) // exactly 1 second
    }

    @Test
    fun seconds_display_only() {
        assertTrue(RationalValue(3, 2).secondsDouble() == 1.5)
    }
}
