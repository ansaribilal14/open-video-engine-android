package app.ove.studio.util

import app.ove.studio.engine.RationalValue
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeCodeTest {

    @Test
    fun formats_mm_ss_mmm() {
        assertEquals("00:01.500", TimeCode.format(RationalValue(3, 2)))
        assertEquals("00:00.000", TimeCode.format(RationalValue.ZERO))
        assertEquals("01:00.000", TimeCode.format(RationalValue(60, 1)))
        assertEquals("02:03.500", TimeCode.format(RationalValue(247, 2))) // 123.5 s
    }

    @Test
    fun snap_to_tick_axis_is_exact_rational() {
        val t = TimeCode.snapToTick(RationalValue(1, 3), 48_000, 1)
        // 1/3 s = 16000 ticks at 48 kHz, reduced back to exact 1/3
        assertEquals(1L, t.num)
        assertEquals(3L, t.den)
    }
}
