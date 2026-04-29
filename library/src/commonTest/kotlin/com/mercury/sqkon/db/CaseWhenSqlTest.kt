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
