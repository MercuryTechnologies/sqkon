package com.mercury.sqkon.db

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.db.paging.KeysetQueryPagingSource
import com.mercury.sqkon.db.paging.OffsetQueryPagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for #119: a load that is discarded as [LoadResult.Invalid] (a concurrent
 * write invalidated it mid-flight) must NOT mark its fetched rows read. Read-tracking is wired
 * in [KeyValueStorage] via `onRowsLoaded = { updateReadAt(it) }`, so asserting `onRowsLoaded`
 * does not fire on a discarded page is a direct, deterministic proxy for "read_at not bumped".
 *
 * The sources are constructed directly (identity `deserialize`, so the target type is [Entity])
 * with a spy `onRowsLoaded`, wiring the same [EntityQueries] provider builders that
 * [KeyValueStorage.selectKeysetPagingSource] / [KeyValueStorage.selectPagingSource] use
 * internally — the spy is the only substitution, replacing the production `updateReadAt` hook.
 */
class PagingReadTrackingTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val testObjectStorage = keyValueStorage<TestObject>(
        "test-object", entityQueries, metadataQueries, mainScope
    )

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    private fun keysetSource(onRowsLoaded: (List<Entity>) -> Unit) =
        KeysetQueryPagingSource<Entity>(
            queryProvider = entityQueries.selectKeyed(entityName = ENTITY_NAME),
            pageBoundariesProvider = entityQueries.selectPageBoundaries(entityName = ENTITY_NAME),
            boundaryForKeyProvider = entityQueries.selectBoundaryForKey(entityName = ENTITY_NAME),
            countQuery = entityQueries.count(ENTITY_NAME),
            transacter = entityQueries,
            context = Dispatchers.Unconfined,
            deserialize = { it },
            pageSize = 10,
            onRowsLoaded = onRowsLoaded,
        )

    private fun offsetSource(onRowsLoaded: (List<Entity>) -> Unit) =
        OffsetQueryPagingSource<Entity>(
            queryProvider = { limit, offset ->
                entityQueries.select(ENTITY_NAME, limit = limit.toLong(), offset = offset.toLong())
            },
            countQuery = entityQueries.count(ENTITY_NAME),
            transacter = entityQueries,
            context = Dispatchers.Unconfined,
            deserialize = { it },
            initialOffset = 0,
            onRowsLoaded = onRowsLoaded,
        )

    @Test
    fun keyset_invalidatedLoad_doesNotMarkRowsRead() = runTest {
        testObjectStorage.insertAll((1..30).map { TestObject() }.associateBy { it.id })
        var rowsLoadedInvoked = false
        val source = keysetSource { rowsLoadedInvoked = true }

        source.invalidate() // simulate a concurrent write invalidating the in-flight load
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        )

        assertTrue(result is LoadResult.Invalid, "Invalidated load must be discarded as Invalid")
        assertFalse(
            rowsLoadedInvoked,
            "onRowsLoaded (→ updateReadAt) must not fire for a page discarded as Invalid",
        )
    }

    @Test
    fun keyset_validLoad_marksRowsRead() = runTest {
        // Guards against over-correcting: a load that is actually returned MUST still
        // report its rows so read-tracking keeps working.
        testObjectStorage.insertAll((1..30).map { TestObject() }.associateBy { it.id })
        var loadedKeys: List<String>? = null
        val source = keysetSource { entities -> loadedKeys = entities.map { it.entity_key } }

        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        )

        assertTrue(result is LoadResult.Page, "A non-invalidated load must return a Page")
        assertEquals(10, loadedKeys?.size, "A returned page must still report its rows to onRowsLoaded")
    }

    @Test
    fun offset_invalidatedLoad_doesNotMarkRowsRead() = runTest {
        testObjectStorage.insertAll((1..30).map { TestObject() }.associateBy { it.id })
        var rowsLoadedInvoked = false
        val source = offsetSource { rowsLoadedInvoked = true }

        source.invalidate()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        )

        assertTrue(result is LoadResult.Invalid, "Invalidated load must be discarded as Invalid")
        assertFalse(
            rowsLoadedInvoked,
            "onRowsLoaded (→ updateReadAt) must not fire for a page discarded as Invalid",
        )
    }

    @Test
    fun offset_validLoad_marksRowsRead() = runTest {
        testObjectStorage.insertAll((1..30).map { TestObject() }.associateBy { it.id })
        var loadedKeys: List<String>? = null
        val source = offsetSource { entities -> loadedKeys = entities.map { it.entity_key } }

        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        )

        assertTrue(result is LoadResult.Page, "A non-invalidated load must return a Page")
        assertEquals(10, loadedKeys?.size, "A returned page must still report its rows to onRowsLoaded")
    }

    private companion object {
        const val ENTITY_NAME = "test-object"
    }
}
