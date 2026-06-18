package com.example.myapplication.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sanity tests on the world constants. They guard the values we ported from
 * the Godot reference so a refactor doesn't quietly change the feel.
 */
class GameConfigTest {
    @Test
    fun viewportMatchesGodot() {
        assertEquals(288f, GameConfig.VIEWPORT_WIDTH, 0f)
        assertEquals(512f, GameConfig.VIEWPORT_HEIGHT, 0f)
    }

    @Test
    fun groundSitsAtFourHundred() {
        assertEquals(400f, GameConfig.GROUND_Y, 0f)
    }

    @Test
    fun birdStartsAtClassicPosition() {
        assertEquals(80f, GameConfig.BIRD_START_X, 0f)
        assertEquals(240f, GameConfig.BIRD_START_Y, 0f)
        assertEquals(34f, GameConfig.BIRD_WIDTH, 0f)
        assertEquals(24f, GameConfig.BIRD_HEIGHT, 0f)
    }

    @Test
    fun physicsConstantsUnchanged() {
        assertEquals(1400f, GameConfig.GRAVITY, 0f)
        assertEquals(-380f, GameConfig.FLAP_VELOCITY, 0f)
        assertEquals(700f, GameConfig.MAX_FALL_SPEED, 0f)
    }

    @Test
    fun pipeConstantsUnchanged() {
        assertEquals(100f, GameConfig.PIPE_SPEED, 0f)
        assertEquals(1.4f, GameConfig.PIPE_INTERVAL_SEC, 0f)
        assertEquals(340f, GameConfig.PIPE_SPAWN_X, 0f)
        assertEquals(-80f, GameConfig.PIPE_KILL_X, 0f)
        assertEquals(180f, GameConfig.PIPE_GAP_MIN_Y, 0f)
        assertEquals(340f, GameConfig.PIPE_GAP_MAX_Y, 0f)
    }
}
