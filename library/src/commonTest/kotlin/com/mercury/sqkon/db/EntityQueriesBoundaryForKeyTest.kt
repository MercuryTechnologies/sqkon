package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

class EntityQueriesBoundaryForKeyTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val storage = keyValueStorage<TestObject>(
        "test-object", entityQueries, metadataQueries, mainScope
    )

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    private fun seedDeterministic(count: Int = 30) {
        val items = (1..count).associate { i ->
            val id = "key-${i.toString().padStart(3, '0')}"
            id to TestObject(id = id, value = i)
        }
        storage.insertAll(items)
    }

    @Test
    fun boundaryForKey_returnsBoundary_whenKeyIsAlreadyABoundary() = runTest {
        seedDeterministic()
        // Default orderBy = entity_key ASC. Boundaries at rn=1,11,21:
        //   "key-001", "key-011", "key-021".
        val factory = entityQueries.selectBoundaryForKey("test-object")
        val result = factory("key-011", 10L).executeAsOneOrNull()
        assertEquals("key-011", result, "Aligned key returns itself")
    }

    @Test
    fun boundaryForKey_snapsInteriorKey_toContainingBoundary() = runTest {
        seedDeterministic()
        // "key-015" is at rn=15, inside the page starting at rn=11 ("key-011").
        val factory = entityQueries.selectBoundaryForKey("test-object")
        val result = factory("key-015", 10L).executeAsOneOrNull()
        assertEquals("key-011", result, "rn=15 snaps to page-start at rn=11")
    }

    @Test
    fun boundaryForKey_snapsDeletedKey_toNearestPrecedingBoundary() = runTest {
        seedDeterministic()
        storage.deleteByKey("key-015")
        // After delete: 29 items. Lookup "key-015" — missing → COALESCE falls back
        // to MAX(rn) WHERE entity_key <= "key-015" = rn=14 (at "key-014").
        // ((14 - 1) / 10) * 10 + 1 = 11 → entity_key at rn=11 = "key-011".
        val factory = entityQueries.selectBoundaryForKey("test-object")
        val result = factory("key-015", 10L).executeAsOneOrNull()
        assertEquals("key-011", result, "Deleted interior key snaps to the boundary that preceded it")
    }
}
