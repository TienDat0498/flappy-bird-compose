package com.example.myapplication.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin tests for the bird. No Android, no Compose.
 */
class BirdTest {
    @Test
    fun flap_setsVelocityToFlapVelocity() {
        val bird = Bird()
        bird.flap()
        assertEquals(GameConfig.FLAP_VELOCITY, bird.velocity, 0f)
    }

    @Test
    fun flap_marksStartedSoReadyStateEnds() {
        val bird = Bird()
        assertTrue(!bird.started)
        bird.flap()
        assertTrue(bird.started)
    }

    @Test
    fun updatePhysics_bobsInPlace_whenNotStarted() {
        val bird = Bird()
        // Default state: !started. updatePhysics should bob, not fall.
        val startY = bird.y
        repeat(10) { bird.updatePhysics(0.016f) }
        // y should still be near the start (small bob amplitude of 4 px).
        assertTrue(
            "bird should bob around anchor, not fall. startY=$startY y=${bird.y}",
            kotlin.math.abs(bird.y - startY) < 10f,
        )
        assertEquals("velocity should stay zero during bob", 0f, bird.velocity, 0f)
    }

    @Test
    fun updatePhysics_appliesGravity_whenStarted() {
        val bird = Bird()
        bird.flap()  // marks started = true
        bird.updatePhysics(0.1f)  // first frame: velocity = -380 + 140 = -240
        bird.updatePhysics(0.1f)  // velocity = -240 + 140 = -100
        bird.updatePhysics(0.1f)  // velocity = -100 + 140 = +40 (now falling)
        // The bird has integrated velocity over the frames, so y should have moved.
        assertTrue("velocity should be positive (falling) after several frames", bird.velocity > 0f)
    }

    @Test
    fun updatePhysics_capsFallSpeedAtMax() {
        val bird = Bird()
        bird.flap()
        // Drive enough frames to overshoot the cap.
        repeat(100) { bird.updatePhysics(0.1f) }
        assertEquals(GameConfig.MAX_FALL_SPEED, bird.velocity, 0f)
    }

    @Test
    fun rotationTiltsTowardRotUp_whenRising() {
        val bird = Bird()
        bird.flap()  // velocity = -380 (rising)
        // After a single frame at 16ms, gravity adds ~22.4 to velocity, so
        // it's still negative (still rising). The rotation target should
        // therefore be at or below 0 (rotated up).
        bird.updatePhysics(0.016f)
        assertTrue(
            "rotation should be at or below 0 (nose up) when still rising, was ${bird.rotation}",
            bird.rotation <= 0f,
        )
    }

    @Test
    fun rotationTiltsTowardRotDown_whenFalling() {
        val bird = Bird()
        bird.flap()
        // Run enough frames to overshoot zero velocity and start falling.
        repeat(40) { bird.updatePhysics(0.016f) }
        assertTrue("rotation should be positive (down) when falling, was ${bird.rotation}", bird.rotation > 0f)
    }

    @Test
    fun flapAnimationCycles_throughThreeFrames() {
        val bird = Bird()
        val start = bird.flapFrame
        bird.updateFlapAnimation(GameConfig.BIRD_FLAP_INTERVAL_SEC)
        assertEquals((start + 1) % 3, bird.flapFrame)
        bird.updateFlapAnimation(GameConfig.BIRD_FLAP_INTERVAL_SEC)
        assertEquals((start + 2) % 3, bird.flapFrame)
        bird.updateFlapAnimation(GameConfig.BIRD_FLAP_INTERVAL_SEC)
        assertEquals(start, bird.flapFrame)  // wraps back to start
    }

    @Test
    fun reset_returnsToStartState() {
        val bird = Bird()
        bird.flap()
        repeat(20) { bird.updatePhysics(0.016f) }
        bird.reset()
        assertEquals(GameConfig.BIRD_START_X, bird.x, 0f)
        assertEquals(GameConfig.BIRD_START_Y, bird.y, 0f)
        assertEquals(0f, bird.velocity, 0f)
        assertTrue(!bird.started)
        assertTrue(bird.alive)
    }

    @Test
    fun hitbox_centersOnBirdPosition() {
        val bird = Bird(x = 100f, y = 200f)
        val hb = bird.hitbox
        assertEquals(100f - GameConfig.BIRD_WIDTH / 2f, hb.left, 0f)
        assertEquals(200f - GameConfig.BIRD_HEIGHT / 2f, hb.top, 0f)
        assertEquals(100f + GameConfig.BIRD_WIDTH / 2f, hb.right, 0f)
        assertEquals(200f + GameConfig.BIRD_HEIGHT / 2f, hb.bottom, 0f)
    }

    @Test
    fun deadBirdIgnoresPhysicsAndFlap() {
        val bird = Bird()
        bird.alive = false
        val startY = bird.y
        val startVel = bird.velocity
        bird.flap()
        bird.updatePhysics(0.1f)
        assertEquals(startY, bird.y, 0f)
        assertEquals(startVel, bird.velocity, 0f)
    }
}
