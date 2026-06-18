package com.example.myapplication.game

/**
 * World constants for the Flappy Bird port.
 *
 * Values are taken from the Godot reference (project/scripts/<name>.gd files plus
 * project.godot) so the Android port reproduces the same gameplay feel.
 */
object GameConfig {
    // Virtual viewport. The original game is 288x512; the Composable letterboxes
    // to fit any device aspect ratio.
    const val VIEWPORT_WIDTH = 288f
    const val VIEWPORT_HEIGHT = 512f

    // Base / ground.
    const val BASE_WIDTH = 336f
    const val BASE_HEIGHT = 112f
    // Top of the base sits at y = 400; the base itself is drawn from 400 to 512.
    // Bird dies when its top hits y = 392 in the Godot script.
    const val GROUND_Y = 400f

    // Bird starting position and sprite size.
    const val BIRD_START_X = 80f
    const val BIRD_START_Y = 240f
    const val BIRD_WIDTH = 34f
    const val BIRD_HEIGHT = 24f

    // Pipes.
    const val PIPE_WIDTH = 52f
    const val PIPE_HEIGHT = 320f // Sprite height; pairs stretch visually by the gap.
    const val PIPE_GAP = 100f
    const val PIPE_SPEED = 100f
    const val PIPE_INTERVAL_SEC = 1.4f
    const val PIPE_SPAWN_X = 340f
    const val PIPE_KILL_X = -80f
    const val PIPE_GAP_MIN_Y = 180f
    const val PIPE_GAP_MAX_Y = 340f

    // Physics.
    const val GRAVITY = 1400f
    const val FLAP_VELOCITY = -380f
    const val MAX_FALL_SPEED = 700f

    // Rotation (radians).
    const val ROT_UP = -0.5236f   // -30 degrees
    const val ROT_DOWN = 1.3963f  // ~80 degrees

    // Frame loop.
    const val MAX_DELTA_SEC = 1f / 30f

    // Bird wing-flap animation cycles at ~10 Hz (100 ms per frame).
    const val BIRD_FLAP_INTERVAL_SEC = 0.1f

    // Restart.
    const val RESTART_DELAY_SEC = 0.8f

    // Score display. Mirrors the Godot reference's score_display.gd: digit
    // sprites are 24 px wide with 2 px spacing, centered horizontally. The
    // live score sits near the top of the viewport; "Best: N" sits below
    // the gameover sprite.
    const val SCORE_DIGIT_WIDTH = 24f
    const val SCORE_DIGIT_SPACING = 2f
    const val SCORE_TOP_Y = 40f
    const val BEST_TEXT_Y = 200f
}
