package com.example.myapplication.game

/**
 * Top-level state of the game. The engine drives transitions; the renderer
 * and input handlers both read the current value.
 */
enum class GameState {
    /** Title screen. Tap to start a run. */
    READY,

    /** Pipes spawn, score counts, collision is live. */
    PLAYING,

    /** World is frozen, awaiting the restart window. */
    GAME_OVER,
}
