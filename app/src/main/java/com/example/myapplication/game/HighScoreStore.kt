package com.example.myapplication.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistent best-score store, backed by DataStore Preferences.
 *
 * Pure JVM-friendly: the class takes a [DataStore] directly (rather than a
 * [android.content.Context]) so unit tests can construct one against a
 * temporary file. Production wiring goes through the
 * [com.example.myapplication.game.flappyDataStore] extension in
 * [HighScoreStoreExt.kt].
 *
 * Concurrency: the DataStore read-modify-write inside [maybeUpdate] is
 * atomic, so racing writes from multiple coroutines converge correctly.
 */
class HighScoreStore(private val store: DataStore<Preferences>) {
    /**
     * The current best score. Emits 0 if no best has been recorded yet.
     * Collectors see updates as soon as [maybeUpdate] finishes its write.
     */
    val best: Flow<Int> = store.data.map { it[KEY_BEST] ?: 0 }

    /**
     * If [score] is greater than the current best, write [score] as the new
     * best. No-op when [score] is 0 or below the current best. Safe to call
     * from any coroutine context.
     */
    suspend fun maybeUpdate(score: Int) {
        if (score <= 0) return
        store.edit { prefs ->
            val cur = prefs[KEY_BEST] ?: 0
            if (score > cur) prefs[KEY_BEST] = score
        }
    }

    private companion object {
        val KEY_BEST = intPreferencesKey("best")
    }
}
