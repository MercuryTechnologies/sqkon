package com.mercury.sqkon.db.internal

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqkonFlowQueryTest {

    private class StaticQuery(private val results: List<Long>) :
        SqkonQuery<Long>(mapper = { c -> c.getLong(0)!! }) {

        private val listeners = mutableSetOf<Listener>()

        override fun addListener(listener: Listener) { listeners += listener }
        override fun removeListener(listener: Listener) { listeners -= listener }

        fun emitChange() { listeners.toList().forEach { it.queryResultsChanged() } }

        override fun <R> execute(mapper: (SqkonCursor) -> R): R {
            val cursor = object : SqkonCursor {
                var idx = -1
                override fun next(): Boolean { idx++; return idx < results.size }
                override fun getLong(index: Int): Long? = results[idx]
                override fun getString(index: Int): String? = null
                override fun getBytes(index: Int): ByteArray? = null
                override fun getDouble(index: Int): Double? = null
                override fun getBoolean(index: Int): Boolean? = null
            }
            return mapper(cursor)
        }
    }

    @Test
    fun asFlow_mapToList_emits_initial_and_on_notify() = runTest {
        val q = StaticQuery(listOf(1, 2, 3))
        q.asFlow().mapToList(Dispatchers.Unconfined).test {
            assertEquals(listOf(1L, 2L, 3L), awaitItem())
            q.emitChange()
            assertEquals(listOf(1L, 2L, 3L), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun asFlow_mapToOne_emits_first() = runTest {
        val q = StaticQuery(listOf(7))
        q.asFlow().mapToOne(Dispatchers.Unconfined).test {
            assertEquals(7L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun asFlow_mapToOneOrNull_emits_null_when_empty() = runTest {
        val q = StaticQuery(emptyList())
        q.asFlow().mapToOneOrNull(Dispatchers.Unconfined).test {
            assertEquals(null, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
