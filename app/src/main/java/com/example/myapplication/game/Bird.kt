package com.example.myapplication.game

/**
 * Pure-data bird. No Android imports — kept JVM-testable.
 *
 * Coordinates are in world space (the 288x512 virtual viewport). The center
 * of the bird is at `(x, y)`; the rendered sprite is centered on that point.
 */
class Bird(
    var x: Float = GameConfig.BIRD_START_X,
    var y: Float = GameConfig.BIRD_START_Y,
    var velocity: Float = 0f,
    var rotation: Float = 0f,
    var alive: Boolean = true,
    var started: Boolean = false,
    /** Which frame of the 3-frame flap animation to draw. */
    var flapFrame: Int = 1,
    /** Accumulator for the flap animation tick (seconds). */
    var flapTimer: Float = 0f,
    /** Y around which the bird bobs in the READY state. */
    var bobAnchor: Float = GameConfig.BIRD_START_Y,
    /** Phase of the bob oscillation, in seconds. */
    var bobPhase: Float = 0f,
) {
    /**
     * Apply one frame of physics. While the bird hasn't started yet (READY
     * state), it bobs around its anchor. After the first flap, gravity and
     * rotation take over.
     */
    fun updatePhysics(dt: Float) {
        if (!alive) return
        if (!started) {
            bobPhase += dt * 4f
            y = bobAnchor + sin(bobPhase) * 4f
            return
        }
        velocity = (velocity + GameConfig.GRAVITY * dt).coerceAtMost(GameConfig.MAX_FALL_SPEED)
        y += velocity * dt

        val t = ((velocity / 500f) + 0.5f).coerceIn(0f, 1f)
        val target = lerpAngle(GameConfig.ROT_UP, GameConfig.ROT_DOWN, t)
        rotation = lerpAngle(rotation, target, (dt * 6f).coerceIn(0f, 1f))
    }

    /**
     * Apply flap input: launches the bird upward and marks it as having
     * started (which disengages the ready-state bob). No-op if dead.
     */
    fun flap() {
        if (!alive) return
        started = true
        velocity = GameConfig.FLAP_VELOCITY
    }

    /**
     * Advance the 3-frame flap animation. Cycles 0 -> 1 -> 2 -> 0 every
     * [GameConfig.BIRD_FLAP_INTERVAL_SEC] seconds.
     */
    fun updateFlapAnimation(dt: Float) {
        if (!alive) return
        flapTimer += dt
        if (flapTimer >= GameConfig.BIRD_FLAP_INTERVAL_SEC) {
            flapTimer -= GameConfig.BIRD_FLAP_INTERVAL_SEC
            flapFrame = (flapFrame + 1) % 3
        }
    }

    /** Reset the bird to its start state. Used between rounds. */
    fun reset() {
        x = GameConfig.BIRD_START_X
        y = GameConfig.BIRD_START_Y
        velocity = 0f
        rotation = 0f
        alive = true
        started = false
        flapFrame = 1
        flapTimer = 0f
        bobAnchor = GameConfig.BIRD_START_Y
        bobPhase = 0f
    }

    /**
     * AABB of the bird in world space. Used by the engine for collision in
     * Chunk 4.
     */
    val hitbox: BirdHitbox
        get() = BirdHitbox(
            left = x - GameConfig.BIRD_WIDTH / 2f,
            top = y - GameConfig.BIRD_HEIGHT / 2f,
            right = x + GameConfig.BIRD_WIDTH / 2f,
            bottom = y + GameConfig.BIRD_HEIGHT / 2f,
        )
}

data class BirdHitbox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Linear interpolation between two angles, taking the short way around the
 * circle so we don't spin through 180 degrees during a single lerp step.
 */
private fun lerpAngle(from: Float, to: Float, t: Float): Float {
    var diff = to - from
    while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
    while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()
    return from + diff * t
}

private fun sin(x: Float): Float = kotlin.math.sin(x)
