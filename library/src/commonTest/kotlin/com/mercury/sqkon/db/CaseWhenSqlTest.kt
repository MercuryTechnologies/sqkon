package com.mercury.sqkon.db

import app.cash.sqldelight.db.SqlPreparedStatement
import com.mercury.sqkon.BaseSealed
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestSealed
import com.mercury.sqkon.TypeOneData
import com.mercury.sqkon.TypeTwoData
import org.junit.Test
import kotlin.test.assertEquals

class CaseWhenSqlTest {

    @Test
    fun case_singleBranch_emitsExpectedSql() {
        val case: CaseWhen<TestObject> = TestObject::sealed.case<TestObject, TestSealed> {
            whenIs<TestSealed.Impl>(TestObject::sealed.then(TestSealed.Impl::boolean))
        }

        val frag = case.toSqlValue()

        assertEquals(
            "(CASE WHEN json_extract(entity.value, ?) = ? THEN json_extract(entity.value, ?) END)",
            frag.sql,
        )
        assertEquals(3, frag.parameters)
        assertEquals(
            listOf("\$.sealed[0]", "Impl", "\$.sealed[1].boolean"),
            captureBoundArgs(frag.parameters, frag.bindArgs),
        )
    }

    @Test
    fun case_rootSealed_viaKClass_emitsExpectedSql() {
        val case: CaseWhen<BaseSealed> = BaseSealed::class.case {
            whenIs<BaseSealed.TypeOne>(
                BaseSealed::class.with(BaseSealed.TypeOne::data) { then(TypeOneData::key) }
            )
            whenIs<BaseSealed.TypeTwo>(
                BaseSealed::class.with(BaseSealed.TypeTwo::data) { then(TypeTwoData::otherValue) }
            )
        }

        val frag = case.toSqlValue()

        assertEquals(6, frag.parameters)
        assertEquals(
            listOf(
                // BaseSealed.TypeOne is a @JvmInline value class wrapping `data`,
                // so JsonPath skips the wrapper -> $[1].key (not $[1].data.key).
                "\$[0]", "TypeOne", "\$[1].key",
                "\$[0]", "TypeTwo", "\$[1].data.otherValue",
            ),
            captureBoundArgs(frag.parameters, frag.bindArgs),
        )
    }

    @Test
    fun caseGt_producesExpectedWhereFragment() {
        val case = TestObject::sealed.case<TestObject, TestSealed> {
            whenIs<TestSealed.Impl>(TestObject::sealed.then(TestSealed.Impl::boolean))
        }

        val q: SqlQuery = (case gt 100L).toSqlQuery(increment = 1)

        assertEquals(null, q.from)
        assertEquals(
            "((CASE WHEN json_extract(entity.value, ?) = ? THEN json_extract(entity.value, ?) END) > ?)",
            q.where,
        )
        assertEquals(4, q.parameters)
        assertEquals(
            listOf("\$.sealed[0]", "Impl", "\$.sealed[1].boolean", 100L),
            captureBoundArgs(q.parameters, q.bindArgs),
        )
    }

    @Test
    fun caseEq_caseNeq_caseLt_caseLte_caseGte_emitCorrectOperators() {
        val case = TestObject::sealed.case<TestObject, TestSealed> {
            whenIs<TestSealed.Impl>(TestObject::sealed.then(TestSealed.Impl::boolean))
        }

        fun assertEndsWith(op: String, w: Where<TestObject>) {
            val sql = w.toSqlQuery(1).where!!
            assertEquals(true, sql.endsWith(") $op ?)"), "expected '$sql' to end with ') $op ?)'")
        }

        assertEndsWith("=",  case eq 1L)
        assertEndsWith("!=", case neq 1L)
        assertEndsWith("<",  case lt 1L)
        assertEndsWith("<=", case lte 1L)
        assertEndsWith(">",  case gt 1L)
        assertEndsWith(">=", case gte 1L)
    }

    @Test
    fun caseIsNull_caseIsNotNull_emitNullPredicates() {
        val case = TestObject::sealed.case<TestObject, TestSealed> {
            whenIs<TestSealed.Impl>(TestObject::sealed.then(TestSealed.Impl::boolean))
        }

        val isNullQ = case.isNull().toSqlQuery(1)
        assertEquals(true, isNullQ.where!!.endsWith(") IS NULL)"))
        assertEquals(3, isNullQ.parameters)

        val isNotNullQ = case.isNotNull().toSqlQuery(1)
        assertEquals(true, isNotNullQ.where!!.endsWith(") IS NOT NULL)"))
        assertEquals(3, isNotNullQ.parameters)
    }

    @Test
    fun case_multipleBranches_withElse_emitsExpectedSql() {
        val case: CaseWhen<TestObject> = TestObject::sealed.case<TestObject, TestSealed> {
            whenIs<TestSealed.Impl>(TestObject::sealed.then(TestSealed.Impl::boolean))
            whenIs<TestSealed.Impl2>(TestObject::sealed.then(TestSealed.Impl2::value))
            elseValue(TestObject::name.builder())
        }

        val frag = case.toSqlValue()

        assertEquals(
            "(CASE " +
                "WHEN json_extract(entity.value, ?) = ? THEN json_extract(entity.value, ?) " +
                "WHEN json_extract(entity.value, ?) = ? THEN json_extract(entity.value, ?) " +
                "ELSE json_extract(entity.value, ?) " +
                "END)",
            frag.sql,
        )
        assertEquals(7, frag.parameters)
        assertEquals(
            listOf(
                "\$.sealed[0]", "Impl", "\$.sealed[1].boolean",
                "\$.sealed[0]", "Impl2", "\$.sealed[1]",
                "\$.name",
            ),
            captureBoundArgs(frag.parameters, frag.bindArgs),
        )
    }
}

/** Replays a bindArgs lambda against a recording prepared statement so tests can assert binds. */
internal fun captureBoundArgs(
    parameters: Int,
    bindArgs: AutoIncrementSqlPreparedStatement.() -> Unit,
): List<Any?> {
    val captured = arrayOfNulls<Any?>(parameters)
    val recorder = object : SqlPreparedStatement {
        override fun bindBoolean(index: Int, boolean: Boolean?) { captured[index] = boolean }
        override fun bindBytes(index: Int, bytes: ByteArray?) { captured[index] = bytes }
        override fun bindDouble(index: Int, double: Double?) { captured[index] = double }
        override fun bindLong(index: Int, long: Long?) { captured[index] = long }
        override fun bindString(index: Int, string: String?) { captured[index] = string }
    }
    val binder = AutoIncrementSqlPreparedStatement(preparedStatement = recorder)
    bindArgs(binder)
    return captured.toList()
}
