package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class StatementCacheEvictionTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val store = keyValueStorage<TestObject>("cache", entityQueries, metadataQueries, mainScope)

    @After fun tearDown() { mainScope.cancel() }

    @Test
    fun manyDistinctQueryShapes_concurrently_returnCorrectResults() = runTest(timeout = 30.seconds) {
        val items = (0..49).map { TestObject(value = it) }.associateBy { it.id }
        store.insertAll(items)

        coroutineScope {
            val results = (1..200).map { shapeIdx ->
                async(Dispatchers.IO) {
                    val limit = (shapeIdx % 50) + 1L
                    val n = entityQueries.select(
                        entityName = "cache",
                        entityKeys = null,
                        where = null,
                        orderBy = emptyList(),
                        limit = limit,
                        offset = null,
                        expiresAt = null,
                    ).executeAsList().size
                    shapeIdx to n
                }
            }.awaitAll()

            results.forEach { (idx, n) ->
                val expected = ((idx % 50) + 1).coerceAtMost(items.size)
                assertEquals(expected, n, "shape $idx expected $expected rows, got $n")
            }
        }
    }

    @Test
    fun parallelInsertUpdateDelete_acrossManyKeys_completesCleanly() = runTest(timeout = 30.seconds) {
        coroutineScope {
            (1..100).map { i ->
                async(Dispatchers.IO) {
                    val key = "k$i"
                    store.insert(key, TestObject(id = key))
                    store.update(key, TestObject(id = key, name = "updated$i"))
                    store.deleteByKey(key)
                }
            }.awaitAll()
        }
        assertEquals(0, store.count().first())
    }
}
