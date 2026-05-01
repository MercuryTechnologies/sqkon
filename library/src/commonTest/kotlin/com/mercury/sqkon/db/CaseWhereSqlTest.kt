package com.mercury.sqkon.db

import com.mercury.sqkon.Order
import com.mercury.sqkon.Shipment
import com.mercury.sqkon.ShipmentStatus
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

    @Test
    fun caseWhere_multipleSealedBranches_emitsExpectedSql() {
        val w: Where<Order> = Order::class.caseWhere {
            whenIs<Order.Active> { with(Order.Active::dueAt) lt 100L }
            whenIs<Order.Pending> { with(Order.Pending::reviewedAt) eq null }
            whenIs<Order.Cancelled> { with(Order.Cancelled::reason) eq "BLOCKED" }
        }

        val q = w.toSqlQuery(1)

        assertEquals(
            "(CASE " +
                "WHEN json_extract(entity.value, ?) = ? THEN (json_extract(entity.value, ?) < ?) " +
                "WHEN json_extract(entity.value, ?) = ? THEN (json_extract(entity.value, ?) IS NULL) " +
                "WHEN json_extract(entity.value, ?) = ? THEN (json_extract(entity.value, ?) = ?) " +
                "END)",
            q.where,
        )
        assertEquals(11, q.parameters)
        assertEquals(
            listOf(
                "\$[0]", "Active", "\$[1].dueAt", 100L,
                "\$[0]", "Pending", "\$[1].reviewedAt",
                "\$[0]", "Cancelled", "\$[1].reason", "BLOCKED",
            ),
            captureBoundArgs(q.parameters, q.bindArgs),
        )
    }

    @Test
    fun caseWhere_withDefault_emitsElseClause() {
        val w: Where<Order> = Order::class.caseWhere {
            whenIs<Order.Active> { with(Order.Active::dueAt) lt 100L }
            default { Order::class.with(Order::id) eq "fallback" }
        }

        val q = w.toSqlQuery(1)

        assertEquals(
            "(CASE " +
                "WHEN json_extract(entity.value, ?) = ? THEN (json_extract(entity.value, ?) < ?) " +
                "ELSE (json_extract(entity.value, ?) = ?) " +
                "END)",
            q.where,
        )
        assertEquals(6, q.parameters)
        assertEquals(
            listOf("\$[0]", "Active", "\$[1].dueAt", 100L, "\$.id", "fallback"),
            captureBoundArgs(q.parameters, q.bindArgs),
        )
    }

    @Test
    fun caseWhere_default_calledTwice_throws() {
        try {
            Order::class.caseWhere {
                whenIs<Order.Active> { with(Order.Active::dueAt) lt 100L }
                default { Order::class.with(Order::id) eq "a" }
                default { Order::class.with(Order::id) eq "b" }
            }
            kotlin.test.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun caseWhere_onlyDefault_isAllowed() {
        // SQL `CASE ELSE x END` is valid (and useful as an unconditional fallback).
        val w: Where<Order> = Order::class.caseWhere {
            default { Order::class.with(Order::id) eq "x" }
        }
        val q = w.toSqlQuery(1)
        assertEquals(
            "(CASE ELSE (json_extract(entity.value, ?) = ?) END)",
            q.where,
        )
    }

    @Test
    fun caseWhere_emptyBuilder_throwsAtFirstUse() {
        val w: Where<Order> = Order::class.caseWhere { }
        try {
            w.toSqlQuery(1)
            kotlin.test.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun caseWhere_branchWithCompoundAndPredicate() {
        val w: Where<Order> = Order::class.caseWhere {
            whenIs<Order.Active> {
                (with(Order.Active::priority) gt 7).and(with(Order.Active::dueAt) lt 100L)
            }
        }

        val q = w.toSqlQuery(1)

        assertEquals(
            "(CASE " +
                "WHEN json_extract(entity.value, ?) = ? " +
                "THEN ((json_extract(entity.value, ?) > ?) AND (json_extract(entity.value, ?) < ?)) " +
                "END)",
            q.where,
        )
        assertEquals(6, q.parameters)
        assertEquals(
            listOf("\$[0]", "Active", "\$[1].priority", 7L, "\$[1].dueAt", 100L),
            captureBoundArgs(q.parameters, q.bindArgs),
        )
    }

    @Test
    fun caseWhere_nested_compilesAndLowers() {
        val w: Where<Order> = Order::class.caseWhere {
            whenIs<Order.Active> {
                Order::class.caseWhere {
                    whenIs<Order.Active> { with(Order.Active::dueAt) lt 100L }
                }
            }
        }

        val q = w.toSqlQuery(1)
        assertEquals(true, q.where!!.contains("CASE WHEN json_extract(entity.value, ?) = ? THEN (CASE"))
    }

    @Test
    fun caseWhere_onProperty_dispatchesByFieldValue() {
        val w: Where<Shipment> = caseWhere(Shipment::status) {
            whenEq(ShipmentStatus.KEPT)     { Shipment::trackerId neq null }
            whenEq(ShipmentStatus.RETURNED) { Shipment::returnedAt gt 1000L }
            default { Shipment::flagged eq true }
        }

        val q = w.toSqlQuery(1)

        assertEquals(
            "(CASE " +
                "WHEN json_extract(entity.value, ?) = ? THEN (json_extract(entity.value, ?) IS NOT NULL) " +
                "WHEN json_extract(entity.value, ?) = ? THEN (json_extract(entity.value, ?) > ?) " +
                "ELSE (json_extract(entity.value, ?) = ?) " +
                "END)",
            q.where,
        )
        assertEquals(
            listOf(
                "\$.status", "KEPT", "\$.trackerId",
                "\$.status", "RETURNED", "\$.returnedAt", 1000L,
                "\$.flagged", true,
            ),
            captureBoundArgs(q.parameters, q.bindArgs),
        )
    }
}
