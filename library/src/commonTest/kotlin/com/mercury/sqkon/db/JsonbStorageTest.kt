package com.mercury.sqkon.db

import app.cash.sqldelight.db.QueryResult
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestObjectChild
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the SQLite JSON1 contract used by Sqkon:
 * - jsonb() round-trip via insert/select
 * - json_extract scalar on top-level fields
 * - json_tree.fullkey agreement with JsonPathBuilder.buildPath()
 *
 * Uses EntityQueries.sqlDriver (@PublishedApi internal) for direct SQL.
 */
class JsonbStorageTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val store = keyValueStorage<TestObject>(
        "jsonb", entityQueries, metadataQueries, mainScope
    )

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    /**
     * Basic insert → select round-trip through jsonb() + json_extract().
     * Verifies the JSONB encoding does not corrupt field values.
     */
    @Test
    fun jsonbRoundTrip_preservesAllFields() = runTest {
        val expected = TestObject()
        store.insert(expected.id, expected)
        val actual = store.selectByKey(expected.id).first()
        assertEquals(expected, actual)
    }

    /**
     * Verifies json_extract() can pull top-level scalar fields from the JSONB column.
     * SQLite stores the value as JSONB; json_extract converts back for reading.
     */
    @Test
    fun jsonExtract_returnsTopLevelScalar() = runTest {
        val expected = TestObject(name = "alpha", value = 42)
        store.insert(expected.id, expected)

        // SqlCursor.next() returns QueryResult<Boolean> in SQLDelight 2.3.x — use .value
        val cursor = entityQueries.sqlDriver.executeQuery(
            identifier = null,
            sql = "SELECT json_extract(value, '$.name'), json_extract(value, '$.value') " +
                "FROM entity WHERE entity_key = ?",
            mapper = { c ->
                c.next().value  // advance; returns Boolean (ignored here — row must exist)
                QueryResult.Value(Pair(c.getString(0)!!, c.getLong(1)!!))
            },
            parameters = 1,
        ) { bindString(0, expected.id) }.value

        assertEquals("alpha", cursor.first)
        assertEquals(42L, cursor.second)
    }

    /**
     * Confirms that JsonPathBuilder.buildPath() produces paths that match the actual
     * json_tree.fullkey values present in the stored JSONB row.
     *
     * Observed buildPath() values (verified by running, not aspirational):
     *   TestObject::name            → "$.name"
     *   TestObject::value           → "$.value"
     *   TestObject::child + createdAt → "$.child.createdAt"
     */
    @Test
    fun jsonTreeFullkey_matchesJsonPathBuilder() = runTest {
        store.insert("k", TestObject())

        // builder() is a top-level inline extension on KProperty1<R,V>
        // then() is a top-level inline extension that chains two properties
        val cases: List<Pair<JsonPathBuilder<*>, String>> = listOf(
            TestObject::name.builder<TestObject, String>() to "$.name",
            TestObject::value.builder<TestObject, Int>() to "$.value",
            TestObject::child.then(TestObjectChild::createdAt) to "$.child.createdAt",
        )

        cases.forEach { (builder, expectedPath) ->
            assertEquals(
                expectedPath,
                builder.buildPath(),
                "buildPath() mismatch — JsonPathBuilder drifted from expected JSON path",
            )

            // json_tree rows for a JSONB cell: fullkey holds the dotted path
            val found = entityQueries.sqlDriver.executeQuery(
                identifier = null,
                sql = "SELECT 1 FROM entity, json_tree(entity.value, '\$') AS jt " +
                    "WHERE entity_key = 'k' AND jt.fullkey = ? LIMIT 1",
                mapper = { c -> QueryResult.Value(c.next().value) },
                parameters = 1,
            ) { bindString(0, expectedPath) }.value

            assertTrue(found == true, "json_tree.fullkey missing path $expectedPath")
        }
    }
}
