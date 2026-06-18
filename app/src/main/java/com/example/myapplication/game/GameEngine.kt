package com.example.myapplication.game

import kotlin.random.Random

/**
 * Owns the bird, pipes, ground scroll, and state machine. Pure Kotlin — no
 * Android imports.
 *
 * The renderer calls [update] once per frame with the elapsed time since the
 * last call (in seconds, clamped) and a flag for whether the player tapped.
 * The engine is the single source of truth for state transitions, collision,
 * scoring, and SFX events; the renderer is a thin layer that reads the
 * snapshot and dispatches SFX to the platform's audio.
 */
class GameEngine(
    private val random: Random = Random.Default,
) {
    private val bird = Bird()
    private val pipes: MutableList<PipePair> = mutableListOf()
    private var spawnAccumulator: Float = 0f
    private var state: GameState = GameState.READY
    private var restartAccumulator: Float = 0f
    private var hitGround: Boolean = false  // tracks the second-stage "die"
    var score: Int = 0
        private set
    /**
     * Score from the most recent run, or null when no run has ended yet.
     * Set on GAME_OVER transition, cleared on startGame/reset. The renderer
     * reads this from the snapshot as the edge trigger for writing to
     * HighScoreStore.
     */
    var lastRunScore: Int? = null
        private set
    var groundOffsetX: Float = 0f
        private set

    /** Latest snapshot, updated each call to [update]. */
    var snapshot: GameSnapshot = GameSnapshot(state = state, bird = bird)
        private set

    /**
     * Advance the simulation by [dt] seconds. If [flapPressed] is true,
     * forward it to the bird or the state machine.
     */
    fun update(dt: Float, flapPressed: Boolean) {
        // Per-frame SFX events. Reset at the end of the call.
        val sfx = SfxEvents()

        // Tap handling. Different behavior per state.
        if (flapPressed) {
            when (state) {
                GameState.READY -> {
                    startGame()
                    // First tap both starts the game and flaps the bird,
                    // matching the Godot reference's main.gd _input handler.
                    bird.flap()
                    sfx.swoosh = true
                    sfx.wing = true
                }
                GameState.PLAYING -> {
                    if (bird.alive) {
                        bird.flap()
                        sfx.wing = true
                    }
                }
                GameState.GAME_OVER -> {
                    if (snapshot.canRestart) {
                        reset()
                    }
                }
            }
        }

        // Tick the world.
        when (state) {
            GameState.READY -> {
                bird.updatePhysics(dt)
                bird.updateFlapAnimation(dt)
            }
            GameState.PLAYING -> {
                bird.updatePhysics(dt)
                bird.updateFlapAnimation(dt)
                val scored = updatePipes(dt)
                if (scored) sfx.point = true
                groundOffsetX += GameConfig.PIPE_SPEED * dt
                checkCollisions(sfx)
            }
            GameState.GAME_OVER -> {
                // Bird keeps falling until it hits the ground, then rests.
                if (bird.alive) {
                    bird.updatePhysics(dt)
                    bird.updateFlapAnimation(dt)
                    if (bird.y + GameConfig.BIRD_HEIGHT / 2f >= GameConfig.GROUND_Y) {
                        bird.y = GameConfig.GROUND_Y - GameConfig.BIRD_HEIGHT / 2f
                        bird.velocity = 0f
                        bird.alive = false
                        hitGround = true
                        sfx.die = true
                    }
                } else {
                    // Bird has come to rest. Tilt to the down rotation.
                    bird.rotation = lerpToward(
                        bird.rotation, GameConfig.ROT_DOWN, 0.25f
                    )
                }
                restartAccumulator += dt
            }
        }

        // Ceiling clamp: bird can't fly above y=0.
        if (bird.y < GameConfig.BIRD_HEIGHT / 2f) {
            bird.y = GameConfig.BIRD_HEIGHT / 2f
            bird.velocity = maxOf(0f, bird.velocity)
        }

        snapshot = GameSnapshot(
            state = state,
            bird = bird,
            pipes = pipes.toList(),
            groundOffsetX = groundOffsetX,
            score = score,
            lastRunScore = lastRunScore,
            canRestart = state == GameState.GAME_OVER &&
                restartAccumulator >= GameConfig.RESTART_DELAY_SEC,
            sfx = sfx,
        )
    }

    /** Reset the world. Called when leaving GAME_OVER back to READY. */
    fun reset() {
        bird.reset()
        pipes.clear()
        spawnAccumulator = 0f
        restartAccumulator = 0f
        score = 0
        lastRunScore = null
        groundOffsetX = 0f
        hitGround = false
        state = GameState.READY
        snapshot = GameSnapshot(state = state, bird = bird)
    }

    /** Enter PLAYING: reset the bird, spawn the first pipe, start the timer. */
    private fun startGame() {
        state = GameState.PLAYING
        score = 0
        lastRunScore = null
        groundOffsetX = 0f
        spawnAccumulator = 0f
        pipes.clear()
        bird.reset()
        spawnPipe()
    }

    /** Spawn a single pipe at SPAWN_X with a random gap Y. */
    private fun spawnPipe() {
        val gapY = random.nextFloat() *
            (GameConfig.PIPE_GAP_MAX_Y - GameConfig.PIPE_GAP_MIN_Y) +
            GameConfig.PIPE_GAP_MIN_Y
        pipes.add(PipePair(x = GameConfig.PIPE_SPAWN_X, gapY = gapY))
    }

    /**
     * Tick each pipe: scroll left, score any that the bird has passed, free
     * any that are past the kill line. Returns true iff a pipe was scored
     * this frame.
     */
    private fun updatePipes(dt: Float): Boolean {
        var scored = false
        spawnAccumulator += dt
        while (spawnAccumulator >= GameConfig.PIPE_INTERVAL_SEC) {
            spawnAccumulator -= GameConfig.PIPE_INTERVAL_SEC
            spawnPipe()
        }

        val it = pipes.iterator()
        while (it.hasNext()) {
            val pipe = it.next()
            pipe.update(dt)
            if (!pipe.passed && pipe.hasPassed(bird.x)) {
                pipe.passed = true
                score += 1
                scored = true
            }
            if (pipe.x < GameConfig.PIPE_KILL_X) {
                it.remove()
            }
        }
        return scored
    }

    /**
     * Check bird AABB against each pipe and the ground. On hit, transition
     * to GAME_OVER, mark the bird dead, freeze the world.
     */
    private fun checkCollisions(sfx: SfxEvents) {
        val hb = bird.hitbox
        // Ground: bird's bottom edge hits GROUND_Y.
        if (hb.bottom >= GameConfig.GROUND_Y) {
            transitionToGameOver(sfx)
            return
        }
        // Pipes.
        for (pipe in pipes) {
            val top = pipe.topRect
            val bottom = pipe.bottomRect
            if (rectsOverlap(hb, top) || rectsOverlap(hb, bottom)) {
                transitionToGameOver(sfx)
                return
            }
        }
    }

    private fun transitionToGameOver(sfx: SfxEvents) {
        state = GameState.GAME_OVER
        // Bird stays "alive" through the fall so updatePhysics keeps running
        // and gravity carries it down to the ground. The GAME_OVER branch
        // marks it dead on ground contact.
        hitGround = false
        restartAccumulator = 0f
        // Capture the final score for this run so the renderer can persist
        // it to HighScoreStore. Cleared on the next startGame/reset.
        lastRunScore = score
        sfx.hit = true
    }

    private fun rectsOverlap(a: BirdHitbox, b: PipeRect): Boolean =
        a.left < b.right && a.right > b.left &&
        a.top < b.bottom && a.bottom > b.top
}

private fun lerpToward(from: Float, to: Float, t: Float): Float = from + (to - from) * t
