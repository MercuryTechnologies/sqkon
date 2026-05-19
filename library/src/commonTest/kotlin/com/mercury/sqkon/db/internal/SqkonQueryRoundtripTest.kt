package com.mercury.sqkon.db.internal

import app.cash.sqldelight.Query
import com.mercury.sqkon.db.driverFactory
import com.mercury.sqkon.db.internal.sqldelight.SqlDelightSqkonDriver
import com.mercury.sqkon.db.internal.sqldelight.toSqkonQuery
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SqkonQueryRoundtripTest {

    private val sqlDriver = driverFactory().createDriver()
    private val driver = SqlDelightSqkonDriver(sqlDriver)

    init {
        driver.executeUpdate(null, "CREATE TABLE q(x INTEGER)", 0)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun selectXQuery(): SqkonQuery<Long> {
        val sqlQuery: Query<Long> = Query(
            identifier = 1,
            queryKeys = arrayOf("q"),
            driver = sqlDriver,
            query = "SELECT x FROM q ORDER BY x",
            mapper = { cursor -> cursor.getLong(0)!! },
        )
        return sqlQuery.toSqkonQuery()
    }

    @Test
    fun executeAsList_empty_then_populated() {
        val q = selectXQuery()
        assertEquals(emptyList(), q.executeAsList())
        driver.executeUpdate(null, "INSERT INTO q VALUES (1),(2),(3)", 0)
        assertEquals(listOf(1L, 2L, 3L), q.executeAsList())
    }

    @Test
    fun executeAsOneOrNull_returns_null_when_empty() {
        assertNull(selectXQuery().executeAsOneOrNull())
    }

    @Test
    fun executeAsOne_throws_when_empty() {
        assertFailsWith<NullPointerException> { selectXQuery().executeAsOne() }
    }

    @Test
    fun listener_fires_on_underlying_driver_notify() {
        var fires = 0
        val q = selectXQuery()
        q.addListener(SqkonQuery.Listener { fires++ })
        sqlDriver.notifyListeners(queryKeys = arrayOf("q"))
        assertEquals(1, fires)
    }

    @Test
    fun removeListener_stops_firing() {
        var fires = 0
        val q = selectXQuery()
        val listener = SqkonQuery.Listener { fires++ }
        q.addListener(listener)
        sqlDriver.notifyListeners(queryKeys = arrayOf("q"))
        q.removeListener(listener)
        sqlDriver.notifyListeners(queryKeys = arrayOf("q"))
        assertEquals(1, fires)
    }
}
