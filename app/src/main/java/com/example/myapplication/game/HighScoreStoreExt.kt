package com.example.myapplication.game

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * App-wide DataStore for the Flappy Bird best score. The
 * [preferencesDataStore] delegate is a process-level singleton keyed on the
 * file name, so calling this from any [Context] (Activity or application)
 * returns the same instance.
 *
 * Use [Context.applicationContext] when accessing from a Composable, so the
 * delegate binds to a long-lived context rather than the Activity.
 */
val Context.flappyDataStore: DataStore<Preferences> by preferencesDataStore(name = "flappy")
