package com.example.myapplication.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.IOException

/**
 * Thin Android-side wrapper over [MediaPlayer] for the five SFX the game
 * needs: wing, hit, die, point, swoosh. Each [play] call rewinds the same
 * prepared player to the start, which is the standard low-latency pattern
 * for short sound effects and is sufficient here because the game never
 * plays the same SFX twice within ~50 ms.
 *
 * The engine stays free of Android imports by emitting [SfxEvents] in its
 * snapshot; the renderer ([FlappyBirdGame]) reads those and asks the
 * [SoundManager] to play. The SoundManager is the only place in the game
 * package that touches the platform.
 */
class SoundManager(private val context: Context) {
    /**
     * Pre-loaded players, one per SFX name. We keep each in a "prepared"
     * state and call `seekTo(0)` + `start()` on every play.
     */
    private val pool: Map<String, MediaPlayer> = buildMap {
        for (name in listOf("wing", "hit", "die", "point", "swoosh")) {
            load("audio/$name.ogg")?.let { put(name, it) }
        }
    }

    /** Play a sound by name. No-op if the asset failed to load. */
    fun play(name: String) {
        val mp = pool[name] ?: return
        try {
            if (mp.isPlaying) mp.pause()
            mp.seekTo(0)
            mp.start()
        } catch (_: IllegalStateException) {
            // Player was released; ignore — the next play will be silent.
        }
    }

    /** Free native resources. Call from the host's onDestroy / onCleared. */
    fun release() {
        for (mp in pool.values) {
            try { mp.release() } catch (_: Throwable) { /* ignore */ }
        }
    }

    private fun load(assetPath: String): MediaPlayer? {
        val afd = try {
            context.assets.openFd(assetPath)
        } catch (_: IOException) {
            return null
        }
        return MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            try {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
            } catch (_: IOException) {
                release()
                return null
            } finally {
                try { afd.close() } catch (_: IOException) { /* ignore */ }
            }
        }
    }
}
