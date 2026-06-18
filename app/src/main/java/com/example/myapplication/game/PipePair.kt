package com.example.myapplication.game

/**
 * A single obstacle: a top pipe, a bottom pipe, and an invisible scorer box
 * between them. The pair scrolls left at [GameConfig.PIPE_SPEED] px/s and is
 * removed by the engine when [x] falls below [GameConfig.PIPE_KILL_X].
 *
 * `gapY` is the vertical center of the gap; the gap spans
 * `[gapY - PIPE_GAP/2, gapY + PIPE_GAP/2]`.
 */
class PipePair(
    var x: Float,
    var gapY: Float,
) {
    /** True once the bird has passed this pair. Used to score once. */
    var passed: Boolean = false

    /** Move the pair left by PIPE_SPEED * dt. */
    fun update(dt: Float) {
        x -= GameConfig.PIPE_SPEED * dt
    }

    /** Has the bird's center crossed past this pair? */
    fun hasPassed(birdX: Float): Boolean = x + GameConfig.PIPE_WIDTH / 2f < birdX

    /** Top pipe AABB in world space. */
    val topRect: PipeRect
        get() = PipeRect(
            left = x - GameConfig.PIPE_WIDTH / 2f,
            top = 0f,
            right = x + GameConfig.PIPE_WIDTH / 2f,
            bottom = gapY - GameConfig.PIPE_GAP / 2f,
        )

    /** Bottom pipe AABB in world space. */
    val bottomRect: PipeRect
        get() = PipeRect(
            left = x - GameConfig.PIPE_WIDTH / 2f,
            top = gapY + GameConfig.PIPE_GAP / 2f,
            right = x + GameConfig.PIPE_WIDTH / 2f,
            bottom = GameConfig.VIEWPORT_HEIGHT.toFloat(),
        )
}

data class PipeRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
