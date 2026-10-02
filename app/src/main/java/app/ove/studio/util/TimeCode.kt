package app.ove.studio.util

import app.ove.studio.engine.RationalValue
import java.util.Locale

/**
 * Timecode formatting for the UI. Display-only: the engine boundary always
 * receives exact rationals (num/den), never these strings.
 */
object TimeCode {
    /** mm:ss.mmm — tabular figures keep the display jitter-free. */
    fun format(t: RationalValue): String {
        val secs = t.secondsDouble().coerceAtLeast(0.0)
        val m = (secs / 60).toInt()
        val s = (secs % 60).toInt()
        val ms = ((secs - (m * 60 + s)) * 1000).toInt().coerceIn(0, 999)
        return String.format(Locale.ROOT, "%02d:%02d.%03d", m, s, ms)
    }

    /** Snap an exact rational to the project tick axis (exact, no float). */
    fun snapToTick(t: RationalValue, tickNum: Long, tickDen: Long): RationalValue {
        // ticks = t * tickNum / tickDen, rounded half-up, then back to seconds
        val n = t.num.toBigInteger().multiply(tickNum.toBigInteger())
        val d = t.den.toBigInteger().multiply(tickDen.toBigInteger())
        val ticks = n.divide(d) // floor; half-up refinement below
        val remainder2 = n.subtract(ticks.multiply(d)).multiply(java.math.BigInteger.TWO)
        val ticksRounded = if (remainder2 >= d) ticks.add(java.math.BigInteger.ONE) else ticks
        // seconds = ticks * tickDen / tickNum
        val sn = ticksRounded.multiply(tickDen.toBigInteger())
        val sd = tickNum.toBigInteger()
        val g = sn.gcd(sd)
        val rn = sn.divide(g)
        val rd = sd.divide(g)
        return RationalValue(rn.longValueExact(), rd.longValueExact())
    }

    private fun Long.toBigInteger() = java.math.BigInteger.valueOf(this)
}
