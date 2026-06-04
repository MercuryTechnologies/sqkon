package com.mercury.sqkon.db

import com.mercury.sqkon.FloatFieldObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Regression test for Float query-value truncation (#72).
 *
 * `bindValue` handled `is Double`, then caught every other `Number` via
 * `is Number -> bindLong(value.toLong())`, so a `Float` query value was silently truncated to a
 * `Long` (`gt 1.9f` bound `1`, `eq 1.5f` bound `1`) — wrong results, no error.
 */
class JsonPathFloatBindingTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val storage = keyValueStorage<FloatFieldObject>(
        "float-field", entityQueries, metadataQueries, mainScope,
    )

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    @Test
    fun gt_floatField_comparesAtFractionalBoundary() = runTest {
        storage.insert("1", FloatFieldObject(id = "1", price = 1.5f))

        // 1.5 is not > 1.9. Before the fix `gt 1.9f` bound 1L, so `1.5 > 1` matched (wrong).
        assertEquals(0, storage.select(where = FloatFieldObject::price gt 1.9f).first().size)
        // 1.5 is > 1.0.
        assertEquals(1, storage.select(where = FloatFieldObject::price gt 1.0f).first().size)
    }

    @Test
    fun eq_floatField_matchesFractionalValue() = runTest {
        storage.insert("1", FloatFieldObject(id = "1", price = 1.5f))

        // Before the fix `eq 1.5f` bound 1L and never matched the stored 1.5.
        assertEquals(1, storage.select(where = FloatFieldObject::price eq 1.5f).first().size)
    }
}
