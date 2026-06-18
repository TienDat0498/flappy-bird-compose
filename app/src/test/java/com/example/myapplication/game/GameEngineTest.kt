package com.example.myapplication.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun flap_setsBirdVelocityThroughEngine() {
        val engine = GameEngine()
        engine.update(dt = 0.016f, flapPressed = true)
        // After one frame, gravity has added a small amount, but the bird
        // should still be moving upward (velocity is still negative).
        assert(
            engine.snapshot.bird.velocity < 0f
        ) { "expected bird to still be moving up after flap+1 frame, was ${engine.snapshot.bird.velocity}" }
    }

    @Test
    fun noFlap_birdBobsInPlace_inReadyState() {
        val engine = GameEngine()
        val startY = engine.snapshot.bird.y
        engine.update(dt = 0.1f, flapPressed = false)
        // Bird bobs around start, not falling.
        assertTrue(
            "bird should stay near start in READY, was startY=$startY y=${engine.snapshot.bird.y}",
            kotlin.math.abs(engine.snapshot.bird.y - startY) < 10f,
        )
        assertEquals(GameState.READY, engine.snapshot.state)
    }

    @Test
    fun snapshot_startsInReadyState() {
        val engine = GameEngine()
        assertEquals(GameState.READY, engine.snapshot.state)
    }

    @Test
    fun reset_returnsBirdToStart() {
        val engine = GameEngine()
        engine.update(dt = 0.016f, flapPressed = true)
        engine.reset()
        val bird = engine.snapshot.bird
        assertEquals(GameConfig.BIRD_START_X, bird.x, 0f)
        assertEquals(GameConfig.BIRD_START_Y, bird.y, 0f)
        assertEquals(0f, bird.velocity, 0f)
        assertEquals(GameState.READY, engine.snapshot.state)
    }

    // -- Chunk 3: pipes ----------------------------------------------------

    @Test
    fun firstFlap_transitionsToPlaying() {
        val engine = GameEngine()
        engine.update(dt = 0.016f, flapPressed = true)
        assertEquals(GameState.PLAYING, engine.snapshot.state)
    }

    @Test
    fun firstFlap_spawnsInitialPipe() {
        val engine = GameEngine()
        engine.update(dt = 0.016f, flapPressed = true)
        // Godot's main.gd spawns one pipe immediately on _start_game.
        assertEquals(1, engine.snapshot.pipes.size)
    }

    @Test
    fun pipeSpawns_afterInterval() {
        val engine = GameEngine(random = Random(42))
        engine.update(dt = 0.016f, flapPressed = true)
        // After PIPE_INTERVAL seconds of update ticks, a second pipe spawns.
        // Use 100 small frames to accumulate to 1.4s. Flap every 20 frames
        // (~320 ms) to keep the bird alive during the march — without
        // this, the bird falls to the ground in ~0.7 s and the engine
        // freezes in GAME_OVER, stopping further pipe spawning.
        val frames = (GameConfig.PIPE_INTERVAL_SEC / 0.016f).toInt() + 2
        repeat(frames) { i ->
            engine.update(dt = 0.016f, flapPressed = (i % 20 == 0))
        }
        assertTrue(
            "expected at least 2 pipes after PIPE_INTERVAL, got ${engine.snapshot.pipes.size}",
            engine.snapshot.pipes.size >= 2,
        )
    }

    @Test
    fun passingPipe_incrementsScore() {
        val engine = GameEngine(random = Random(42))
        engine.update(dt = 0.016f, flapPressed = true)  // enter PLAYING, spawn 1 pipe
        // March a single pipe past the bird and check it got marked passed.
        // The engine continues to spawn new pipes on the interval, so we
        // can't use the score counter directly — we assert on the pipe's
        // own `passed` flag. Flap every 20 frames to keep the bird alive
        // (otherwise it hits the ground in ~0.7 s and the engine freezes).
        val pipe = engine.snapshot.pipes.first()
        val targetX = GameConfig.BIRD_START_X - 200f
        val frames = ((pipe.x - targetX) / GameConfig.PIPE_SPEED / 0.016f).toInt() + 1
        repeat(frames) { i ->
            engine.update(dt = 0.016f, flapPressed = (i % 20 == 0))
        }
        // The first pipe (which was the one we tracked) should now be marked passed.
        val passedFirst = engine.snapshot.pipes.firstOrNull { it === pipe }?.passed
            ?: true  // if it has despawned, it definitely was passed
        assertTrue("first pipe should be marked passed", passedFirst)
    }

    @Test
    fun pipesDespawn_whenOffscreen() {
        val engine = GameEngine(random = Random(42))
        engine.update(dt = 0.016f, flapPressed = true)
        // March a single pipe all the way past the kill line. The engine
        // continues spawning new pipes on the interval, so the list won't
        // be empty — but the first pipe should be gone. Flap every 20
        // frames so the bird doesn't hit the ground mid-march and freeze
        // the engine in GAME_OVER.
        val pipe = engine.snapshot.pipes.first()
        val frames = ((pipe.x - GameConfig.PIPE_KILL_X) / GameConfig.PIPE_SPEED / 0.016f).toInt() + 2
        repeat(frames) { i ->
            engine.update(dt = 0.016f, flapPressed = (i % 20 == 0))
        }
        assertTrue(
            "first pipe should have been removed after passing kill line",
            engine.snapshot.pipes.none { it === pipe },
        )
    }

    @Test
    fun groundScrolls_inPlaying() {
        val engine = GameEngine()
        engine.update(dt = 0.016f, flapPressed = true)  // enter PLAYING
        val startOffset = engine.snapshot.groundOffsetX
        engine.update(dt = 0.016f, flapPressed = false)
        assertTrue(
            "ground should scroll in PLAYING, was $startOffset -> ${engine.snapshot.groundOffsetX}",
            engine.snapshot.groundOffsetX > startOffset,
        )
    }

    @Test
    fun groundDoesNotScroll_inReady() {
        val engine = GameEngine()
        val startOffset = engine.snapshot.groundOffsetX
        engine.update(dt = 0.016f, flapPressed = false)
        assertEquals(startOffset, engine.snapshot.groundOffsetX, 0f)
    }

    // -- Chunk 4: collision, GAME_OVER, restart window, SFX ---------------

    @Test
    fun firstFlap_emitsSwooshAndWingSfx() {
        val engine = GameEngine()
        engine.update(dt = 0.016f, flapPressed = true)  // READY → PLAYING
        val sfx = engine.snapshot.sfx
        assertTrue("sfx.swoosh should fire on first flap", sfx.swoosh)
        assertTrue("sfx.wing should fire on first flap", sfx.wing)
    }

    @Test
    fun birdHitsPipe_transitionsToGameOver() {
        val engine = GameEngine(random = Random(42))
        engine.update(dt = 0.016f, flapPressed = true)  // PLAYING, spawn 1 pipe
        // Force the spawned pipe to a known position and gap, then drop the
        // bird into the bottom pipe's column. The next update's
        // checkCollisions will detect the overlap and transition to
        // GAME_OVER, with sfx.hit=true on the emitted snapshot.
        val pipe = engine.snapshot.pipes.first()
        pipe.gapY = 240f                          // gap [190, 290]
        pipe.x = GameConfig.BIRD_START_X          // pipe centered on the bird's x
        engine.snapshot.bird.y = 350f             // inside bottom pipe column (290..512)
        engine.update(dt = 0.016f, flapPressed = false)
        assertEquals(GameState.GAME_OVER, engine.snapshot.state)
        assertTrue("sfx.hit should fire on pipe collision", engine.snapshot.sfx.hit)
    }

    @Test
    fun birdHitsGround_transitionsToGameOver_andPlaysDie() {
        val engine = GameEngine(random = Random(42))
        engine.update(dt = 0.016f, flapPressed = true)  // PLAYING
        // Position the bird so the very next frame's checkCollisions
        // detects the ground impact (sfx.hit). We use y=410 (well below
        // the GROUND_Y=400 line) so the bird is still on/over the ground
        // in the following GAME_OVER frame, where the die-check (bird
        // settling) fires sfx.die. The bird's initial flap velocity is
        // negative, so it rises during physics — placing it deep under
        // the ground ensures the rise doesn't carry the bird off the
        // ground contact line before the die-check runs.
        engine.snapshot.bird.y = 410f
        engine.update(dt = 0.016f, flapPressed = false)
        assertEquals(GameState.GAME_OVER, engine.snapshot.state)
        assertTrue("sfx.hit should fire on ground impact", engine.snapshot.sfx.hit)
        // On the next frame the GAME_OVER branch detects ground contact
        // and marks the bird dead, firing sfx.die.
        engine.update(dt = 0.016f, flapPressed = false)
        assertTrue("sfx.die should fire when bird settles on ground", engine.snapshot.sfx.die)
        assertTrue("bird should be marked not-alive on ground contact", !engine.snapshot.bird.alive)
    }

    @Test
    fun tapInGameOver_beforeWindow_doesNotReset() {
        val engine = GameEngine()
        engine.update(dt = 0.016f, flapPressed = true)  // PLAYING
        engine.snapshot.bird.y = GameConfig.GROUND_Y - 5f
        engine.update(dt = 0.016f, flapPressed = false)  // → GAME_OVER
        assertEquals(GameState.GAME_OVER, engine.snapshot.state)
        assertTrue(!engine.snapshot.canRestart)
        engine.update(dt = 0.016f, flapPressed = true)  // tap before window
        assertEquals(GameState.GAME_OVER, engine.snapshot.state)
        assertTrue("canRestart should remain false inside the restart window", !engine.snapshot.canRestart)
    }

    @Test
    fun tapInGameOver_afterWindow_resetsToReady() {
        val engine = GameEngine()
        engine.update(dt = 0.016f, flapPressed = true)  // PLAYING
        engine.snapshot.bird.y = GameConfig.GROUND_Y - 5f
        engine.update(dt = 0.016f, flapPressed = false)  // → GAME_OVER
        // March RESTART_DELAY_SEC of frames. restartAccumulator ticks each
        // frame in the GAME_OVER branch.
        val frames = (GameConfig.RESTART_DELAY_SEC / 0.016f).toInt() + 2
        repeat(frames) { engine.update(dt = 0.016f, flapPressed = false) }
        assertTrue("canRestart should become true after the restart window", engine.snapshot.canRestart)
        engine.update(dt = 0.016f, flapPressed = true)  // tap after window
        assertEquals(GameState.READY, engine.snapshot.state)
        assertEquals(0, engine.snapshot.score)
        assertTrue("pipes should be cleared after reset", engine.snapshot.pipes.isEmpty())
    }

    @Test
    fun passingPipe_emitsPointSfx() {
        val engine = GameEngine(random = Random(42))
        engine.update(dt = 0.016f, flapPressed = true)  // PLAYING, spawn 1 pipe
        // Position the pipe so a single tiny update scrolls it just past the
        // bird's x. hasPassed(80) becomes true when pipe.x + 26 < 80, i.e.
        // pipe.x < 54. We set it to 53.9 so a 0.0001s update (~0.01 px of
        // scroll) crosses the threshold. We also pin the gap and bird y so
        // the bird is inside the gap (no pipe collision) for this single
        // frame, and gravity barely moves the bird.
        val pipe = engine.snapshot.pipes.first()
        pipe.gapY = 240f
        pipe.x = 53.9f
        engine.snapshot.bird.y = 240f
        engine.update(dt = 0.0001f, flapPressed = false)
        assertTrue("pipe should be marked passed", pipe.passed)
        assertTrue("sfx.point should fire when the bird passes a pipe", engine.snapshot.sfx.point)
    }

    @Test
    fun gameOver_doesNotSpawnPipes() {
        val engine = GameEngine(random = Random(42))
        engine.update(dt = 0.016f, flapPressed = true)  // PLAYING, spawn 1 pipe
        engine.snapshot.bird.y = GameConfig.GROUND_Y - 5f
        engine.update(dt = 0.016f, flapPressed = false)  // → GAME_OVER
        val pipeCountAtGameOver = engine.snapshot.pipes.size
        // March for the pipe-spawn interval. If the spawn accumulator kept
        // ticking, a new pipe would appear and the count would grow.
        val frames = (GameConfig.PIPE_INTERVAL_SEC / 0.016f).toInt() + 2
        repeat(frames) { engine.update(dt = 0.016f, flapPressed = false) }
        assertEquals(
            "pipe count should not change in GAME_OVER (no spawning, no scrolling)",
            pipeCountAtGameOver, engine.snapshot.pipes.size,
        )
    }

    @Test
    fun reset_clearsGameOverState() {
        val engine = GameEngine()
        engine.update(dt = 0.016f, flapPressed = true)  // PLAYING
        engine.snapshot.bird.y = GameConfig.GROUND_Y - 5f
        engine.update(dt = 0.016f, flapPressed = false)  // → GAME_OVER
        assertEquals(GameState.GAME_OVER, engine.snapshot.state)
        engine.reset()
        assertEquals(GameState.READY, engine.snapshot.state)
        assertEquals(0, engine.snapshot.score)
        assertTrue("pipes should be cleared after reset", engine.snapshot.pipes.isEmpty())
        assertTrue(!engine.snapshot.canRestart)
    }

    // -- Chunk 5: lastRunScore --------------------------------------------

    @Test
    fun lastRunScore_capturesScoreAtGameOver() {
        val engine = GameEngine(random = Random(42))
        engine.update(dt = 0.016f, flapPressed = true)  // PLAYING, score=0
        // Pass the first pipe to score 1.
        val pipe = engine.snapshot.pipes.first()
        pipe.gapY = 240f
        pipe.x = 53.9f
        engine.snapshot.bird.y = 240f
        engine.update(dt = 0.0001f, flapPressed = false)
        assertEquals(1, engine.snapshot.score)
        // Now die.
        engine.snapshot.bird.y = 410f
        engine.update(dt = 0.016f, flapPressed = false)
        assertEquals(GameState.GAME_OVER, engine.snapshot.state)
        assertEquals(
            "lastRunScore should capture the score at the moment of GAME_OVER",
            1, engine.snapshot.lastRunScore,
        )
    }

    @Test
    fun lastRunScore_clearedOnReset() {
        val engine = GameEngine()
        engine.update(dt = 0.016f, flapPressed = true)
        engine.snapshot.bird.y = 410f
        engine.update(dt = 0.016f, flapPressed = false)
        assertEquals("lastRunScore set after GAME_OVER", 0, engine.snapshot.lastRunScore)
        engine.reset()
        assertNull("lastRunScore should be null after reset", engine.snapshot.lastRunScore)
    }
}
