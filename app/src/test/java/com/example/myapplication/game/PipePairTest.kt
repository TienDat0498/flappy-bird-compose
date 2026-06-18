package com.example.myapplication.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipePairTest {
    @Test
    fun update_movesLeft() {
        val pipe = PipePair(x = 100f, gapY = 250f)
        pipe.update(0.1f)
        // 100 px/s * 0.1s = 10 px
        assertEquals(90f, pipe.x, 0f)
    }

    @Test
    fun hasPassed_trueOnceBirdCenterIsPastRightEdge() {
        val pipe = PipePair(x = 100f, gapY = 250f)
        // Pipe right edge is at 100 + 26 = 126. Bird at 80 is not past.
        assertFalse(pipe.hasPassed(80f))
        // Bird at 127 is past.
        assertTrue(pipe.hasPassed(127f))
    }

    @Test
    fun topRect_coversFromTopOfWorldToGapTop() {
        val pipe = PipePair(x = 100f, gapY = 250f)
        val r = pipe.topRect
        assertEquals(100f - GameConfig.PIPE_WIDTH / 2f, r.left, 0f)
        assertEquals(0f, r.top, 0f)
        assertEquals(100f + GameConfig.PIPE_WIDTH / 2f, r.right, 0f)
        assertEquals(250f - GameConfig.PIPE_GAP / 2f, r.bottom, 0f)
    }

    @Test
    fun bottomRect_coversFromGapBottomToBottomOfWorld() {
        val pipe = PipePair(x = 100f, gapY = 250f)
        val r = pipe.bottomRect
        assertEquals(100f - GameConfig.PIPE_WIDTH / 2f, r.left, 0f)
        assertEquals(250f + GameConfig.PIPE_GAP / 2f, r.top, 0f)
        assertEquals(100f + GameConfig.PIPE_WIDTH / 2f, r.right, 0f)
        assertEquals(GameConfig.VIEWPORT_HEIGHT, r.bottom, 0f)
    }
}
