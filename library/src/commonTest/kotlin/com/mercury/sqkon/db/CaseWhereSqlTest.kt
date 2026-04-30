package com.mercury.sqkon.db

import com.mercury.sqkon.Order
import org.junit.Test
import kotlin.test.assertEquals

class CaseWhereSqlTest {

    @Test
    fun caseWhere_singleSealedBranch_emitsExpectedSql() {
        val w: Where<Order> = Order::class.caseWhere {
            whenIs<Order.Active> {
                with(Order.Active::dueAt) lt 100L
            }
        }

        val q = w.toSqlQuery(increment = 1)

        assertEquals(null, q.from)
        assertEquals(
            "(CASE " +
                "WHEN json_extract(entity.value, ?) = ? " +
                "THEN (json_extract(entity.value, ?) < ?) " +
                "END)",
            q.where,
        )
        assertEquals(4, q.parameters)
        assertEquals(
            listOf("\$[0]", "Active", "\$[1].dueAt", 100L),
            captureBoundArgs(q.parameters, q.bindArgs),
        )
    }
}
