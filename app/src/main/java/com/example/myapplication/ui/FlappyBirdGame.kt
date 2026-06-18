package com.example.myapplication.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.myapplication.game.GameConfig
import com.example.myapplication.game.GameEngine
import com.example.myapplication.game.GameState
import com.example.myapplication.game.HighScoreStore
import com.example.myapplication.game.PipePair
import com.example.myapplication.game.SfxEvents
import com.example.myapplication.game.SoundManager
import com.example.myapplication.game.flappyDataStore

/**
 * Flappy Bird game view.
 *
 * Owns a [GameEngine] and drives it from a [withFrameNanos] loop. Renders
 * the world in a letterbox of the virtual 288x512 viewport.
 *
 * Render order (back to front, matching the Godot reference):
 *   1. Background
 *   2. Bottom pipes (their gap-side cap is visible at the gap bottom)
 *   3. Bird
 *   4. Top pipes (flipped vertically, cap at the gap top)
 *   5. Ground
 *   6. HUD overlay: live score (PLAYING), message sprite (READY), or
 *      gameover sprite + "Best: N" / "New best!" (GAME_OVER)
 */
@Composable
fun FlappyBirdGame(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val background = remember { loadAssetImage(context, "sprites/background-day.png") }
    val base = remember { loadAssetImage(context, "sprites/base.png") }
    val message = remember { loadAssetImage(context, "sprites/message.png") }
    val gameover = remember { loadAssetImage(context, "sprites/gameover.png") }
    val pipe = remember { loadAssetImage(context, "sprites/pipe-green.png") }
    val birdFrames = remember {
        arrayOf(
            loadAssetImage(context, "sprites/yellowbird-upflap.png"),
            loadAssetImage(context, "sprites/yellowbird-midflap.png"),
            loadAssetImage(context, "sprites/yellowbird-downflap.png"),
        )
    }
    // Digit sprites for the live score. Index 0 is "0.png", index 9 is
    // "9.png"; the array order matches the file names so we can look up
    // each digit by its int value.
    val digits = remember {
        Array(10) { i -> loadAssetImage(context, "sprites/$i.png") }
    }

    // SoundManager is created once per Composable lifetime and released on
    // disposal. The engine emits SfxEvents on each snapshot; we dispatch
    // them to the SoundManager from a LaunchedEffect keyed on the snapshot
    // value, so each edge-triggered event fires exactly once.
    val sound = remember { SoundManager(context) }
    DisposableEffect(sound) { onDispose { sound.release() } }

    // HighScoreStore: singleton DataStore on the application context (not
    // the Activity) so it survives configuration changes and is shared
    // across the process. bestScore is collected as Compose state so the
    // UI re-renders when the persisted value updates.
    val highScoreStore = remember(context) {
        HighScoreStore(context.applicationContext.flappyDataStore)
    }
    val bestScore by produceState(initialValue = 0, highScoreStore) {
        highScoreStore.best.collect { value = it }
    }
    // Latch: true for the duration of the GAME_OVER screen when this run
    // just set a new high. Reset to false when the player restarts (state
    // leaves GAME_OVER). Drives the "New best!" line under gameover.
    var justSetNewBest by remember { mutableStateOf(false) }

    val engine = remember { GameEngine() }
    val snapshot = remember { mutableStateOf(engine.snapshot, referentialEqualityPolicy()) }
    val flapPressed = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 0f
                else ((now - lastNanos) / 1_000_000_000f)
                    .coerceIn(0f, GameConfig.MAX_DELTA_SEC)
                lastNanos = now
                engine.update(dt, flapPressed.value)
                flapPressed.value = false
                snapshot.value = engine.snapshot
            }
        }
    }

    // Dispatch SFX events from each new snapshot. The engine produces a
    // fresh snapshot per frame and clears the per-frame SfxEvents at the
    // end of each update, so this effect re-runs once per frame and each
    // event plays exactly once.
    LaunchedEffect(snapshot.value) {
        val sfx = snapshot.value.sfx
        if (sfx != SfxEvents.NONE) {
            if (sfx.wing) sound.play("wing")
            if (sfx.hit) sound.play("hit")
            if (sfx.die) sound.play("die")
            if (sfx.point) sound.play("point")
            if (sfx.swoosh) sound.play("swoosh")
        }
    }

    // Persist a new high score when the engine finishes a run. The effect
    // re-keys on (lastRunScore, bestScore): when lastRunScore transitions
    // from null to a number (run finished), and that number is greater
    // than the current best, we write to DataStore. bestScore re-keying
    // also catches the case where the same run is processed multiple
    // times — maybeUpdate is a no-op when the new value isn't a high.
    LaunchedEffect(snapshot.value.lastRunScore, bestScore) {
        val run = snapshot.value.lastRunScore ?: return@LaunchedEffect
        if (run > bestScore) {
            highScoreStore.maybeUpdate(run)
            justSetNewBest = true
        }
    }

    // Reset the new-best latch when the player restarts. Without this, a
    // subsequent GAME_OVER for a non-record run would briefly show the
    // "New best!" line.
    LaunchedEffect(snapshot.value.state) {
        if (snapshot.value.state != GameState.GAME_OVER) {
            justSetNewBest = false
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val bestStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val newBestStyle = bestStyle.copy(color = Color.Yellow)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { flapPressed.value = true })
            }
    ) {
        val scaleFactor = minOf(
            size.width / GameConfig.VIEWPORT_WIDTH,
            size.height / GameConfig.VIEWPORT_HEIGHT
        )
        val drawW = GameConfig.VIEWPORT_WIDTH * scaleFactor
        val drawH = GameConfig.VIEWPORT_HEIGHT * scaleFactor
        val offsetX = (size.width - drawW) / 2f
        val offsetY = (size.height - drawH) / 2f

        translate(offsetX, offsetY) {
            scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                // Clip to the 288x512 world region so sprites that extend
                // past the world (e.g. the top pipe's column reaching
                // y = -90 for gapY=180, or the bottom pipe's column reaching
                // y = 650 for gapY=280) don't bleed into the black
                // letterbox. The clipRect is inside the scale block so the
                // rect is in world coordinates, not device pixels.
                clipRect(
                    left = 0f,
                    top = 0f,
                    right = GameConfig.VIEWPORT_WIDTH,
                    bottom = GameConfig.VIEWPORT_HEIGHT,
                    clipOp = androidx.compose.ui.graphics.ClipOp.Intersect,
                ) {
                    val s = snapshot.value
                    drawBackground(background)
                    for (p in s.pipes) {
                        drawBottomPipe(pipe, p)
                    }
                    drawBird(birdFrames[s.bird.flapFrame], s.bird.x, s.bird.y, s.bird.rotation)
                    for (p in s.pipes) {
                        drawTopPipe(pipe, p)
                    }
                    drawBase(base, s.groundOffsetX)
                    when (s.state) {
                        GameState.READY -> drawMessage(message)
                        GameState.PLAYING -> drawScore(digits, s.score)
                        GameState.GAME_OVER -> {
                            drawGameover(gameover)
                            drawBest(textMeasurer, bestStyle, bestScore)
                            if (justSetNewBest) {
                                drawNewBest(textMeasurer, newBestStyle, bestScore)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBackground(image: ImageBitmap) {
    drawImage(image = image, topLeft = Offset(0f, 0f))
}

private fun DrawScope.drawMessage(image: ImageBitmap) {
    val cx = GameConfig.VIEWPORT_WIDTH / 2f - image.width / 2f
    val cy = GameConfig.VIEWPORT_HEIGHT / 2f - image.height / 2f - 30f
    drawImage(image = image, topLeft = Offset(cx, cy))
}

/**
 * "Game Over" sprite, centered horizontally and placed in the upper third
 * of the world (y ≈ 128). Mirrors the Godot `HUD/GameOver` position. Drawn
 * on top of the base, behind any future score overlay.
 */
private fun DrawScope.drawGameover(image: ImageBitmap) {
    val cx = GameConfig.VIEWPORT_WIDTH / 2f - image.width / 2f
    val cy = GameConfig.VIEWPORT_HEIGHT * 0.25f - image.height / 2f
    drawImage(image = image, topLeft = Offset(cx, cy))
}

/**
 * Live score, drawn in the top center of the world. Mirrors the Godot
 * `score_display.gd` layout: each digit is [GameConfig.SCORE_DIGIT_WIDTH]
 * px wide with [GameConfig.SCORE_DIGIT_SPACING] px between digits, all
 * centered horizontally as a group, anchored at the top of the viewport.
 * No digits are drawn for a score of 0 (matches Godot's empty-children
 * behavior on the title screen).
 */
private fun DrawScope.drawScore(digits: Array<ImageBitmap>, score: Int) {
    if (score <= 0) return
    val s = score.toString()
    val digitW = GameConfig.SCORE_DIGIT_WIDTH
    val spacing = GameConfig.SCORE_DIGIT_SPACING
    val totalW = s.length * (digitW + spacing) - spacing
    var x = (GameConfig.VIEWPORT_WIDTH - totalW) / 2f
    val y = GameConfig.SCORE_TOP_Y
    for (ch in s) {
        val d = ch.digitToInt()
        val img = digits[d]
        drawImage(image = img, topLeft = Offset(x, y))
        x += digitW + spacing
    }
}

/**
 * "Best: N" line, drawn centered horizontally just below the gameover
 * sprite. The text size is set in the [style] parameter so callers can
 * share the TextStyle instance with [drawNewBest].
 */
private fun DrawScope.drawBest(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    best: Int,
) {
    val text = "Best: $best"
    val layout = textMeasurer.measure(text, style)
    val x = (GameConfig.VIEWPORT_WIDTH - layout.size.width) / 2f
    drawText(layout, topLeft = Offset(x, GameConfig.BEST_TEXT_Y))
}

/**
 * "New best!" line, drawn in [newBestStyle] (typically yellow) one line
 * below the "Best: N" line. Rendered as plain ASCII (no special chars) so
 * we don't need a font asset — the default Compose font renders it fine.
 */
private fun DrawScope.drawNewBest(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    best: Int,
) {
    val text = "New best!"
    val layout = textMeasurer.measure(text, style)
    val x = (GameConfig.VIEWPORT_WIDTH - layout.size.width) / 2f
    // 22 px below the Best: line; sized to roughly one line of text in
    // the same style.
    drawText(layout, topLeft = Offset(x, GameConfig.BEST_TEXT_Y + 24f))
}

private fun DrawScope.drawBird(image: ImageBitmap, x: Float, y: Float, rotationDeg: Float) {
    val angleDeg = Math.toDegrees(rotationDeg.toDouble()).toFloat()
    rotate(degrees = angleDeg, pivot = Offset(x, y)) {
        drawImage(
            image = image,
            topLeft = Offset(
                x - image.width / 2f,
                y - image.height / 2f,
            ),
        )
    }
}

private fun DrawScope.drawBase(image: ImageBitmap, offsetX: Float) {
    val baseY = GameConfig.GROUND_Y
    val tilesToCover = (GameConfig.VIEWPORT_WIDTH / image.width).toInt() + 2
    var x = -((offsetX % image.width + image.width) % image.width)
    repeat(tilesToCover) {
        drawImage(image = image, topLeft = Offset(x, baseY))
        x += image.width
    }
}

/**
 * Bottom pipe: sprite at native size, top edge at the gap's bottom edge.
 * The sprite's "lip" is the top 20-30 px of the source (the full-width
 * cap); the rest of the source is the narrower column. Drawing the sprite
 * with its top at gapY+50 places the lip at the gap bottom and the column
 * below it. The column extends down past the ground; the ground (drawn
 * on top) covers any portion below y = GROUND_Y.
 */
private fun DrawScope.drawBottomPipe(image: ImageBitmap, pipe: PipePair) {
    val top = pipe.gapY + GameConfig.PIPE_GAP / 2f
    drawImage(
        image = image,
        topLeft = Offset(pipe.x - image.width / 2f, top),
    )
}

/**
 * Top pipe: same sprite, drawn at native size, then rotated 180° around
 * the sprite's center. The sprite's TOP (the lip) lands at the gap top
 * after rotation, and the column extends upward to y = gapY - 370. Mirrors
 * the Godot `pipe_pair.tscn` TopPipe (position = (0, -210), rotation = π).
 *
 * The visible area is world y = [gapY - 370, gapY - 50]; for typical
 * gapY=280, that's y = [-90, 230] — the top of the column gets clipped
 * off the world top, just like in the Godot reference.
 */
private fun DrawScope.drawTopPipe(image: ImageBitmap, pipe: PipePair) {
    val centerX = pipe.x
    val centerY = pipe.gapY - 210f
    rotate(degrees = 180f, pivot = Offset(centerX, centerY)) {
        drawImage(
            image = image,
            topLeft = Offset(centerX - image.width / 2f, centerY - image.height / 2f),
        )
    }
}

private fun loadAssetImage(context: Context, path: String): ImageBitmap {
    context.assets.open(path).use { input ->
        val bitmap = BitmapFactory.decodeStream(input)
            ?: error("Failed to decode asset $path")
        return bitmap.asImageBitmap()
    }
}
