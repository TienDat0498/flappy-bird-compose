package com.example.myapplication.game

/**
 * Read-only view of the world at one tick. The renderer reads this each
 * frame; the engine is the only thing that produces it.
 */
data class GameSnapshot(
    val state: GameState,
    val bird: Bird,
    val pipes: List<PipePair> = emptyList(),
    /**
     * X offset of the ground tile pair, in world pixels. Positive = the
     * ground has scrolled left.
     */
    val groundOffsetX: Float = 0f,
    /** Current score. Increments as pipes are passed in PLAYING. */
    val score: Int = 0,
    /**
     * Score from the most recent run, set on GAME_OVER transition and
     * cleared on the next startGame/reset. The renderer uses this as the
     * edge trigger for writing to HighScoreStore.
     */
    val lastRunScore: Int? = null,
    /**
     * True once the restart window has elapsed in GAME_OVER. The renderer
     * should accept taps to reset only when this is true.
     */
    val canRestart: Boolean = false,
    /**
     * Edge-triggered SFX events for this frame. The renderer checks these
     * and asks the [SoundManager] to play. The engine clears them at the
     * end of each [GameEngine.update] call so each event is delivered
     * exactly once.
     */
    val sfx: SfxEvents = SfxEvents.NONE,
)

/** Edge-triggered sound effects produced by the engine. */
data class SfxEvents(
    var wing: Boolean = false,
    var hit: Boolean = false,
    var die: Boolean = false,
    var point: Boolean = false,
    var swoosh: Boolean = false,
) {
    companion object {
        val NONE = SfxEvents()
    }
}
