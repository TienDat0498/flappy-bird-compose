package com.example.myapplication.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [HighScoreStore]. Runs on the JVM (no Robolectric) by
 * constructing a DataStore directly against a temp file — DataStore
 * Preferences' JVM implementation supports this without an Android Context.
 */
class HighScoreStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: HighScoreStore

    @Before
    fun setUp() {
        // PreferenceDataStoreFactory.create will create and own the file;
        // the .preferences_pb suffix isn't required but matches the
        // production naming convention.
        file = File(tmp.newFolder(), "flappy.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        store = HighScoreStore(dataStore)
    }

    @After
    fun tearDown() {
        // Each test gets its own tmp folder so the file is cleaned up
        // automatically, but we close the DataStore explicitly to avoid
        // lingering file handles.
        runCatching { /* DataStore has no public close in 1.1.x; rely on GC */ }
    }

    @Test
    fun best_defaultsToZero() = runTest {
        assertEquals(0, store.best.first())
    }

    @Test
    fun maybeUpdate_writesNewHigh() = runTest {
        store.maybeUpdate(5)
        assertEquals(5, store.best.first())
    }

    @Test
    fun maybeUpdate_doesNotLowerExistingHigh() = runTest {
        store.maybeUpdate(10)
        store.maybeUpdate(3)
        assertEquals(10, store.best.first())
    }

    @Test
    fun maybeUpdate_ignoresZeroAndNegative() = runTest {
        store.maybeUpdate(0)
        assertEquals(0, store.best.first())
        store.maybeUpdate(-5)
        assertEquals(0, store.best.first())
    }

    @Test
    fun maybeUpdate_canBeCalledMultipleTimesForNewHighs() = runTest {
        store.maybeUpdate(2)
        store.maybeUpdate(5)
        store.maybeUpdate(3)   // not a new high
        store.maybeUpdate(10)
        assertEquals(10, store.best.first())
    }
}