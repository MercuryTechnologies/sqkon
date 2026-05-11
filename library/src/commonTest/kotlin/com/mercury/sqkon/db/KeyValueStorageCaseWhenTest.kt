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

    /**
     * The canonical case/when expression for these tests:
     *   when Active  -> $.activatedAt
     *   when Pending -> $.requestedAt
     */
    private fun timeCase(): CaseWhen<SealedTimed> = SealedTimed::class.case {
        whenIs<SealedTimed.Active>(
            SealedTimed::class.with(SealedTimed.Active::activatedAt)
        )
        whenIs<SealedTimed.Pending>(
            SealedTimed::class.with(SealedTimed.Pending::requestedAt)
        )
    }

    private val SealedTimed.id: String
        get() = when (this) {
            is SealedTimed.Active -> id
            is SealedTimed.Pending -> id
        }

    @Test
    fun where_caseEq_picksRightFieldPerVariant() = runTest {
        // Active rows hold the value in `activatedAt`; Pending rows hold it in `requestedAt`.
        // `time eq 100L` should match the row in EACH variant whose own field equals 100,
        // because CASE picks the right path per row's discriminator.
        storage.insertAll(mapOf(
            "a-100" to SealedTimed.Active(id = "a-100", activatedAt = 100L),  // active match
            "a-50"  to SealedTimed.Active(id = "a-50",  activatedAt = 50L),
            "p-100" to SealedTimed.Pending(id = "p-100", requestedAt = 100L), // pending match
            "p-50"  to SealedTimed.Pending(id = "p-50",  requestedAt = 50L),
        ))
        val time = timeCase()

        // CASE eq 100 picks whichever variant's field equals 100.
        val matches100 = storage.select(where = time eq 100L).first()
        assertEquals(setOf("a-100", "p-100"), matches100.map { it.id }.toSet())

        // CASE eq 50 picks the other pair.
        val matches50 = storage.select(where = time eq 50L).first()
        assertEquals(setOf("a-50", "p-50"), matches50.map { it.id }.toSet())
    }

    @Test
    fun where_caseGt_filtersAcrossVariantsByLogicalValue() = runTest {
        // Pivot at 150: Active rows past activatedAt > 150, Pending rows past requestedAt > 150.
        storage.insertAll(mapOf(
            "a-100" to SealedTimed.Active(id = "a-100", activatedAt = 100L),
            "a-300" to SealedTimed.Active(id = "a-300", activatedAt = 300L), // active > 150
            "p-200" to SealedTimed.Pending(id = "p-200", requestedAt = 200L), // pending > 150
            "p-100" to SealedTimed.Pending(id = "p-100", requestedAt = 100L),
        ))

        val above150 = storage.select(where = timeCase() gt 150L).first()

        assertEquals(setOf("a-300", "p-200"), above150.map { it.id }.toSet())
    }

    @Test
    fun where_caseGt_combinedWith_jsonTreeEq_viaAnd() = runTest {
        // Two SQL patterns coexist in one query:
        //   - timeCase() gt 150L  (CASE/json_extract scalar)
        //   - SealedTimed.Active::id eq "a-300"  (json_tree LATERAL join)
        storage.insertAll(mapOf(
            "a-100" to SealedTimed.Active(id = "a-100", activatedAt = 100L),
            "a-300" to SealedTimed.Active(id = "a-300", activatedAt = 300L),
            "p-200" to SealedTimed.Pending(id = "p-200", requestedAt = 200L),
            "p-300" to SealedTimed.Pending(id = "p-300", requestedAt = 300L),
        ))

        val activeAbove150WithSpecificId = storage.select(
            where = (timeCase() gt 150L) and (
                SealedTimed::class.with(SealedTimed.Active::id) eq "a-300"
            ),
        ).first()

        assertEquals(1, activeAbove150WithSpecificId.size)
        assertEquals("a-300", (activeAbove150WithSpecificId.single() as SealedTimed.Active).id)
    }

    @Test
    fun where_caseIsNull_matchesRowsWithNoMatchingBranch() = runTest {
        storage.insertAll(mapOf(
            "a-100" to SealedTimed.Active(id = "a-100", activatedAt = 100L),
            "p-200" to SealedTimed.Pending(id = "p-200", requestedAt = 200L),
        ))
        // CASE with only an Active branch -> Pending rows fall through to NULL.
        val activeOnly: CaseWhen<SealedTimed> = SealedTimed::class.case {
            whenIs<SealedTimed.Active>(
                SealedTimed::class.with(SealedTimed.Active::activatedAt)
            )
        }

        val nulls = storage.select(where = activeOnly.isNull()).first()
        assertEquals(listOf("p-200"), nulls.map { it.id })

        val nonNulls = storage.select(where = activeOnly.isNotNull()).first()
        assertEquals(listOf("a-100"), nonNulls.map { it.id })
    }

    @Test
    fun orderBy_caseExpression_ordersAcrossVariantsByLogicalTimestamp() = runTest {
        // Each row's id encodes its logical value so the expected order is obvious.
        storage.insertAll(mapOf(
            "a-100" to SealedTimed.Active(id = "a-100", activatedAt = 100L),
            "p-400" to SealedTimed.Pending(id = "p-400", requestedAt = 400L),
            "a-300" to SealedTimed.Active(id = "a-300", activatedAt = 300L),
            "p-200" to SealedTimed.Pending(id = "p-200", requestedAt = 200L),
        ))

        val ordered = storage.select(
            orderBy = listOf(OrderBy(timeCase(), OrderDirection.DESC)),
        ).first()

        // Sorted by per-variant value DESC: 400, 300, 200, 100.
        assertEquals(listOf("p-400", "a-300", "p-200", "a-100"), ordered.map { it.id })
    }
}
