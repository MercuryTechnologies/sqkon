package com.mercury.sqkon.db

import com.mercury.sqkon.Order
import com.mercury.sqkon.Shipment
import com.mercury.sqkon.ShipmentStatus
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyValueStorageCaseWhereTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val orders = keyValueStorage<Order>(
        "orders", entityQueries, metadataQueries, mainScope,
    )
    private val shipments = keyValueStorage<Shipment>(
        "shipments", entityQueries, metadataQueries, mainScope,
    )

    @AfterTest fun tearDown() { mainScope.cancel() }

    /** Example 1: trivial single sealed branch — Pending/Cancelled excluded. */
    @Test fun example1_singleBranch_excludesOtherVariants() = runTest {
        orders.insertAll(mapOf(
            "a" to Order.Active(id = "a", dueAt = 100L, priority = 5),
            "p" to Order.Pending(id = "p", reviewedAt = null),
        ))

        val urgentActive = orders.select(where = Order::class.caseWhere {
            whenIs<Order.Active> { with(Order.Active::priority) gt 3 }
        }).first()

        assertEquals(listOf("a"), urgentActive.map { it.id })
    }

    /** Example 2: different fields, operators, RHS types per variant. */
    @Test fun example2_perVariantFieldsAndOps() = runTest {
        orders.insertAll(mapOf(
            "a-due"     to Order.Active(id = "a-due", dueAt = 50L, priority = 1),
            "a-ok"      to Order.Active(id = "a-ok",  dueAt = 200L, priority = 1),
            "p-stale"   to Order.Pending(id = "p-stale", reviewedAt = null),
            "p-fresh"   to Order.Pending(id = "p-fresh", reviewedAt = 99L),
            "c-blocked" to Order.Cancelled(id = "c-blocked", reason = "BLOCKED"),
            "c-other"   to Order.Cancelled(id = "c-other",   reason = "DUPLICATE"),
        ))

        val needsAttention = orders.select(where = Order::class.caseWhere {
            whenIs<Order.Active>    { with(Order.Active::dueAt) lt 100L }
            whenIs<Order.Pending>   { with(Order.Pending::reviewedAt) eq null }
            whenIs<Order.Cancelled> { with(Order.Cancelled::reason) eq "BLOCKED" }
        }).first()

        assertEquals(setOf("a-due", "p-stale", "c-blocked"), needsAttention.map { it.id }.toSet())
    }

    /** Example 3: discriminator-field dispatch (non-sealed). */
    @Test fun example3_discriminatorFieldDispatch() = runTest {
        shipments.insertAll(mapOf(
            "k1" to Shipment(id = "k1", status = ShipmentStatus.KEPT,       trackerId = "T1"),
            "k2" to Shipment(id = "k2", status = ShipmentStatus.KEPT,       trackerId = null),
            "r1" to Shipment(id = "r1", status = ShipmentStatus.RETURNED,   returnedAt = 5000L),
            "r2" to Shipment(id = "r2", status = ShipmentStatus.RETURNED,   returnedAt = 100L),
            "i1" to Shipment(id = "i1", status = ShipmentStatus.IN_TRANSIT),
        ))

        val visible = shipments.select(where = caseWhere(Shipment::status) {
            whenEq(ShipmentStatus.KEPT)     { Shipment::trackerId neq null }
            whenEq(ShipmentStatus.RETURNED) { Shipment::returnedAt gt 1000L }
        }).first()

        assertEquals(setOf("k1", "r1"), visible.map { it.id }.toSet())
    }

    /** Example 4: composes with regular and/or under the outer where. */
    @Test fun example4_composesWithOuterAndOr() = runTest {
        orders.insertAll(mapOf(
            "a-mine"  to Order.Active(id = "a-mine",  dueAt = 10L, priority = 1),
            "a-other" to Order.Active(id = "a-other", dueAt = 10L, priority = 1),
            "p-mine"  to Order.Pending(id = "p-mine", reviewedAt = null),
        ))

        val needsAttentionMine = orders.select(where =
            Order::class.caseWhere {
                whenIs<Order.Active>  { with(Order.Active::dueAt) lt 100L }
                whenIs<Order.Pending> { with(Order.Pending::reviewedAt) eq null }
            }.and(Order::class.with(Order.Active::id) like "%-mine"),
        ).first()

        assertEquals(setOf("a-mine", "p-mine"), needsAttentionMine.map { it.id }.toSet())
    }

    /** Example 5: compound predicate within a single branch. */
    @Test fun example5_compoundBranchPredicate() = runTest {
        orders.insertAll(mapOf(
            "a-hi-due" to Order.Active(id = "a-hi-due", dueAt = 10L,  priority = 9),
            "a-hi-ok"  to Order.Active(id = "a-hi-ok",  dueAt = 999L, priority = 9),
            "a-lo-due" to Order.Active(id = "a-lo-due", dueAt = 10L,  priority = 1),
        ))

        val highAndDue = orders.select(where = Order::class.caseWhere {
            whenIs<Order.Active> {
                (with(Order.Active::priority) gt 5)
                    .and(with(Order.Active::dueAt) lt 100L)
            }
        }).first()

        assertEquals(listOf("a-hi-due"), highAndDue.map { it.id })
    }

    /** Example 6 (stretch): nested caseWhere inside a branch. */
    @Test fun example6_nestedCaseWhere() = runTest {
        orders.insertAll(mapOf(
            "a-hi" to Order.Active(id = "a-hi", dueAt = 10L, priority = 9),
            "a-lo" to Order.Active(id = "a-lo", dueAt = 10L, priority = 1),
            "p"    to Order.Pending(id = "p", reviewedAt = null),
        ))

        val w = Order::class.caseWhere {
            whenIs<Order.Active> {
                Order::class.caseWhere {
                    whenIs<Order.Active> { with(Order.Active::priority) gt 5 }
                }
            }
            whenIs<Order.Pending> { with(Order.Pending::reviewedAt) eq null }
        }
        val matched = orders.select(where = w).first()

        assertEquals(setOf("a-hi", "p"), matched.map { it.id }.toSet())
    }

    /** Default branch falls back when no variant matches. */
    @Test fun default_fallsBackForUnmatchedVariants() = runTest {
        orders.insertAll(mapOf(
            "a" to Order.Active(id = "a", dueAt = 10L, priority = 1),
            "p" to Order.Pending(id = "p", reviewedAt = null),
            "c" to Order.Cancelled(id = "c", reason = "BLOCKED"),
        ))

        val w = Order::class.caseWhere {
            whenIs<Order.Active> { with(Order.Active::dueAt) lt 100L } // matches a
            default { Order::class.with(Order.Cancelled::id) eq "c" } // matches c
        }
        val matched = orders.select(where = w).first()

        assertEquals(setOf("a", "c"), matched.map { it.id }.toSet())
    }

    /** No default and no matching branch -> row falls through to NULL/falsy. */
    @Test fun noDefault_unmatchedVariantsExcluded() = runTest {
        orders.insertAll(mapOf(
            "a" to Order.Active(id = "a", dueAt = 10L, priority = 1),
            "p" to Order.Pending(id = "p", reviewedAt = null),
        ))

        val w = Order::class.caseWhere {
            whenIs<Order.Active> { with(Order.Active::dueAt) lt 100L }
            // no Pending branch, no default
        }
        val matched = orders.select(where = w).first()

        assertEquals(listOf("a"), matched.map { it.id })
    }

    private val Order.id: String
        get() = when (this) {
            is Order.Active    -> id
            is Order.Pending   -> id
            is Order.Cancelled -> id
        }
}
