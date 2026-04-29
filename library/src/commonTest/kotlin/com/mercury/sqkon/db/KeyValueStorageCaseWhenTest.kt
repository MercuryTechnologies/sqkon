package com.mercury.sqkon.db

import com.mercury.sqkon.SealedTimed
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyValueStorageCaseWhenTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val storage = keyValueStorage<SealedTimed>(
        "sealed-timed", entityQueries, metadataQueries, mainScope,
    )

    @AfterTest fun tearDown() { mainScope.cancel() }

    private fun timeCase(): CaseWhen<SealedTimed> = SealedTimed::class.case {
        whenIs<SealedTimed.Active>(
            SealedTimed::class.with(SealedTimed.Active::activatedAt)
        )
        whenIs<SealedTimed.Pending>(
            SealedTimed::class.with(SealedTimed.Pending::requestedAt)
        )
    }

    @Test
    fun where_caseGt_filtersAcrossVariants() = runTest {
        storage.insertAll(mapOf(
            "a1" to SealedTimed.Active(id = "a1", activatedAt = 100L),
            "a2" to SealedTimed.Active(id = "a2", activatedAt = 300L),
            "p1" to SealedTimed.Pending(id = "p1", requestedAt = 200L),
            "p2" to SealedTimed.Pending(id = "p2", requestedAt = 400L),
        ))

        val above250 = storage.select(where = timeCase() gt 250L).first()
        val ids = above250.map {
            when (it) { is SealedTimed.Active -> it.id; is SealedTimed.Pending -> it.id }
        }.sorted()

        assertEquals(listOf("a2", "p2"), ids)
    }

    @Test
    fun where_caseGt_combinedWith_jsonTreeEq_viaAnd() = runTest {
        storage.insertAll(mapOf(
            "a1" to SealedTimed.Active(id = "a1", activatedAt = 100L),
            "a2" to SealedTimed.Active(id = "a2", activatedAt = 300L),
            "p1" to SealedTimed.Pending(id = "p1", requestedAt = 250L),
            "p2" to SealedTimed.Pending(id = "p2", requestedAt = 400L),
        ))

        val activeAndAbove150 = storage.select(
            where = (timeCase() gt 150L) and (
                SealedTimed::class.with(SealedTimed.Active::id) eq "a2"
            ),
        ).first()

        assertEquals(1, activeAndAbove150.size)
        assertEquals("a2", (activeAndAbove150.single() as SealedTimed.Active).id)
    }

    @Test
    fun where_caseIsNull_matchesRowsWithNoMatchingBranch() = runTest {
        storage.insertAll(mapOf(
            "a1" to SealedTimed.Active(id = "a1", activatedAt = 100L),
            "p1" to SealedTimed.Pending(id = "p1", requestedAt = 200L),
        ))

        // CaseWhen with only an Active branch -> Pending rows fall through to NULL
        val activeOnly: CaseWhen<SealedTimed> = SealedTimed::class.case {
            whenIs<SealedTimed.Active>(
                SealedTimed::class.with(SealedTimed.Active::activatedAt)
            )
        }

        val nulls = storage.select(where = activeOnly.isNull()).first()
        assertEquals(1, nulls.size)
        assertEquals("p1", (nulls.single() as SealedTimed.Pending).id)

        val nonNulls = storage.select(where = activeOnly.isNotNull()).first()
        assertEquals(1, nonNulls.size)
        assertEquals("a1", (nonNulls.single() as SealedTimed.Active).id)
    }

    @Test
    fun orderBy_caseExpression_ordersAcrossVariantsByLogicalTimestamp() = runTest {
        storage.insertAll(mapOf(
            "a1" to SealedTimed.Active(id = "a1", activatedAt = 100L),
            "p1" to SealedTimed.Pending(id = "p1", requestedAt = 400L),
            "a2" to SealedTimed.Active(id = "a2", activatedAt = 300L),
            "p2" to SealedTimed.Pending(id = "p2", requestedAt = 200L),
        ))

        val ordered = storage.select(
            orderBy = listOf(OrderBy(timeCase(), OrderDirection.DESC)),
        ).first()

        val ids = ordered.map {
            when (it) { is SealedTimed.Active -> it.id; is SealedTimed.Pending -> it.id }
        }
        assertEquals(listOf("p1", "a2", "p2", "a1"), ids)
    }
}
