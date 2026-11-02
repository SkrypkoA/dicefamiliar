package com.skrypko.dicefamiliar.ui.dice.render3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Deterministic tumble: spin that decays into the resting orientation, plus slide and bounce. */
object DieAnimation {

    /** Decelerating easing (cubic ease-out). */
    fun ease(t: Double): Double = 1 - (1 - t.coerceIn(0.0, 1.0)).pow(3)

    /** Remaining spin angle at time [t] in [0, 1]. */
    fun spinAngle(t: Double, turns: Double): Double = turns * 2 * PI * (1 - ease(t))

    /** Orientation at [t]: equals [target] when t = 1. */
    fun orientation(t: Double, target: Mat3, spinAxis: Vec3, turns: Double): Mat3 =
        target * Mat3.rotation(spinAxis, spinAngle(t, turns))

    /**
     * Spin axis lies in the screen plane, perpendicular to the swipe, so dice roll "away" in the
     * swipe direction; each die gets a slightly different tilt so they do not move in lockstep.
     */
    fun spinAxis(directionX: Double, directionY: Double, index: Int): Vec3 {
        val tilt = ((index * 37) % 11 - 5) / 10.0
        return Vec3(-directionY, directionX, 0.0).let { if (it.length == 0.0) Vec3.Y else it }
            .plus(Vec3(0.0, 0.0, tilt)).normalized()
    }

    /** Number of full turns for a die; grows with the tumble duration. */
    fun turns(durationMs: Int, index: Int): Double = 1.5 + durationMs / 600.0 + (index % 3) * 0.25

    /** Height (fraction of die size) of a damped bounce at [t]. */
    fun bounce(t: Double): Double = abs(sin(t * PI * 3)) * exp(-3 * t) * (1 - t)
}
