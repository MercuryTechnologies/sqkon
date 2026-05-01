package com.mercury.sqkon.db

import app.cash.sqldelight.db.SqlPreparedStatement
import kotlin.reflect.KProperty1

/**
 * Scalar `(json_extract(entity.value, ?) <op> ?)` fragment, with `IS NULL` /
 * `IS NOT NULL` fall-through for null values when [nullOpSql] is provided.
 */
private fun scalarBinop(
    builder: JsonPathBuilder<*>,
    opSql: String,
    value: Any?,
    nullOpSql: String? = null,
): SqlValueFragment {
    if (value == null && nullOpSql != null) return SqlValueFragment(
        sql = "(json_extract(entity.value, ?) $nullOpSql)",
        parameters = 1,
        bindArgs = { bindString(builder.buildPath()) },
    )
    return SqlValueFragment(
        sql = "(json_extract(entity.value, ?) $opSql ?)",
        parameters = 2,
        bindArgs = {
            bindString(builder.buildPath())
            bindValue(value)
        },
    )
}

/**
 * Scalar `(json_extract(entity.value, ?) [NOT ]IN (?, ?, ...))` fragment, with
 * a constant short-circuit when [values] is empty.
 */
private fun scalarInOp(
    builder: JsonPathBuilder<*>,
    notIn: Boolean,
    values: Collection<*>,
): SqlValueFragment {
    if (values.isEmpty()) return SqlValueFragment(
        sql = if (notIn) "(1)" else "(0)",
        parameters = 0,
        bindArgs = {},
    )
    val placeholders = values.joinToString(", ") { "?" }
    val keyword = if (notIn) "NOT IN" else "IN"
    return SqlValueFragment(
        sql = "(json_extract(entity.value, ?) $keyword ($placeholders))",
        parameters = 1 + values.size,
        bindArgs = {
            bindString(builder.buildPath())
            values.forEach { bindValue(it) }
        },
    )
}

/**
 * Equivalent to `=` in SQL
 */
data class Eq<T : Any, V>(
    private val builder: JsonPathBuilder<T>, private val value: V?,
) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        // SQLite `<col> = NULL` always evaluates to NULL (never true), so for `eq null`
        // we drop the json_tree join and emit `json_extract(entity.value, ?) IS NULL`
        // — matching the scalar lowering's null special-case.
        if (value == null) {
            return SqlQuery(
                from = null,
                where = "(json_extract(entity.value, ?) IS NULL)",
                parameters = 1,
                bindArgs = { bindString(builder.buildPath()) },
            )
        }
        val treeName = "eq_$increment"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "($treeName.fullkey LIKE ? AND $treeName.value = ?)",
            parameters = 2,
            bindArgs = {
                bindString(builder.buildPath())
                bindValue(value)
            }
        )
    }

    override fun toScalarSqlValue(): SqlValueFragment =
        scalarBinop(builder, opSql = "=", value = value, nullOpSql = "IS NULL")
}

/**
 * Equivalent to `=` in SQL
 */
infix fun <T : Any, V> JsonPathBuilder<T>.eq(value: V?): Eq<T, V> =
    Eq(builder = this, value = value)

/**
 * Equivalent to `=` in SQL
 */
inline infix fun <reified T : Any, reified V, VALUE> KProperty1<T, V>.eq(value: VALUE?): Eq<T, VALUE> =
    Eq(this.builder(), value)

/**
 * Equivalent to `!=` in SQL
 */
data class NotEq<T : Any, V>(
    private val builder: JsonPathBuilder<T>, private val value: V?,
) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        // SQLite `<col> != NULL` always evaluates to NULL (never true), so for `neq null`
        // we drop the json_tree join and emit `json_extract(entity.value, ?) IS NOT NULL`
        // — matching the scalar lowering's null special-case.
        if (value == null) {
            return SqlQuery(
                from = null,
                where = "(json_extract(entity.value, ?) IS NOT NULL)",
                parameters = 1,
                bindArgs = { bindString(builder.buildPath()) },
            )
        }
        val treeName = "eq_$increment"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "($treeName.fullkey LIKE ? AND $treeName.value != ?)",
            parameters = 2,
            bindArgs = {
                bindString(builder.buildPath())
                bindValue(value)
            }
        )
    }

    override fun toScalarSqlValue(): SqlValueFragment =
        scalarBinop(builder, opSql = "!=", value = value, nullOpSql = "IS NOT NULL")
}

/**
 * Equivalent to `!=` in SQL
 */
infix fun <T : Any, V> JsonPathBuilder<T>.neq(value: V?): NotEq<T, V> =
    NotEq(builder = this, value = value)

/**
 * Equivalent to `!=` in SQL
 */
inline infix fun <reified T : Any, reified V, VALUE> KProperty1<T, V>.neq(value: VALUE?): NotEq<T, VALUE> =
    NotEq(this.builder(), value)

/**
 * Equivalent to `IN` in SQL
 */
data class In<T : Any, V>(
    private val builder: JsonPathBuilder<T>, private val value: Collection<V>,
) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        val treeName = "in_$increment"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "($treeName.fullkey LIKE ? AND $treeName.value IN (${value.joinToString(",") { "?" }}))",
            parameters = 1 + value.size,
            bindArgs = {
                bindString(builder.buildPath())
                value.forEach { bindValue(it) }
            }
        )
    }

    override fun toScalarSqlValue(): SqlValueFragment =
        scalarInOp(builder, notIn = false, values = value)
}

data class NotIn<T : Any, V>(
    private val builder: JsonPathBuilder<T>, private val value: Collection<V>,
) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        val treeName = "in_$increment"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "($treeName.fullkey LIKE ? AND $treeName.value NOT IN (${value.joinToString(",") { "?" }}))",
            parameters = 1 + value.size,
            bindArgs = {
                bindString(builder.buildPath())
                value.forEach { bindValue(it) }
            }
        )
    }

    override fun toScalarSqlValue(): SqlValueFragment =
        scalarInOp(builder, notIn = true, values = value)
}


/**
 * Equivalent to `IN` in SQL
 */
infix fun <T : Any, V> JsonPathBuilder<T>.inList(value: Collection<V>): In<T, V> =
    In(builder = this, value = value)

/**
 * Equivalent to `IN` in SQL
 */
inline infix fun <reified T : Any, reified V, reified C> KProperty1<T, V>.inList(value: Collection<C>): In<T, C> =
    In<T, C>(builder = this.builder<T, V>(), value = value)

/**
 * Equivalent to `NOT IN` in SQL
 */
infix fun <T : Any, V> JsonPathBuilder<T>.notInList(value: Collection<V>): NotIn<T, V> =
    NotIn(builder = this, value = value)

/**
 * Equivalent to `NOT IN` in SQL
 */
inline fun <reified T : Any, reified V, reified C> KProperty1<T, V>.notInList(value: Collection<C>): NotIn<T, C> =
    NotIn<T, C>(builder = this.builder<T, V>(), value = value)

/**
 * Equivalent to `LIKE` in SQL
 */
data class Like<T : Any>(
    private val builder: JsonPathBuilder<T>, private val value: String?,
) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        val treeName = "like_$increment"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "($treeName.fullkey LIKE ? AND $treeName.value LIKE ?)",
            parameters = 2,
            bindArgs = {
                bindString(builder.buildPath())
                bindString(value)
            }
        )
    }

    override fun toScalarSqlValue(): SqlValueFragment =
        scalarBinop(builder, opSql = "LIKE", value = value)
}

/**
 * Equivalent to `LIKE` in SQL
 */
infix fun <T : Any> JsonPathBuilder<T>.like(value: String?): Like<T> =
    Like(builder = this, value = value)

/**
 * Equivalent to `LIKE` in SQL
 */
inline infix fun <reified T : Any, reified V> KProperty1<T, V>.like(value: String?): Like<T> =
    Like(this.builder(), value)

/**
 * Equivalent to `>` in SQL
 *
 * @param value note that gt will only really work with numbers right now.
 */
data class GreaterThan<T : Any, V>(
    private val builder: JsonPathBuilder<T>, private val value: V?,
) : Where<T>() {

    override fun toSqlQuery(increment: Int): SqlQuery {
        val treeName = "gt_$increment"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "($treeName.fullkey LIKE ? AND $treeName.value > ?)",
            parameters = 2,
            bindArgs = {
                bindString(builder.buildPath())
                bindValue(value)
            }
        )
    }

    override fun toScalarSqlValue(): SqlValueFragment =
        scalarBinop(builder, opSql = ">", value = value)
}

/**
 * Equivalent to `>` in SQL
 */
infix fun <T : Any, V> JsonPathBuilder<T>.gt(value: V?): GreaterThan<T, V> =
    GreaterThan(builder = this, value = value)

/**
 * Equivalent to `>` in SQL
 */
inline infix fun <reified T : Any, reified V, VALUE> KProperty1<T, V>.gt(value: VALUE?): GreaterThan<T, VALUE> =
    GreaterThan(this.builder(), value)


/**
 * Equivalent to `<` in SQL
 */
data class LessThan<T : Any, V>(
    private val builder: JsonPathBuilder<T>, private val value: V?,
) : Where<T>() {

    override fun toSqlQuery(increment: Int): SqlQuery {
        val treeName = "lt_$increment"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "($treeName.fullkey LIKE ? AND $treeName.value < ?)",
            parameters = 2,
            bindArgs = {
                bindString(builder.buildPath())
                bindValue(value)
            }
        )
    }

    override fun toScalarSqlValue(): SqlValueFragment =
        scalarBinop(builder, opSql = "<", value = value)
}

/**
 * Equivalent to `<` in SQL
 */
infix fun <T : Any, V> JsonPathBuilder<T>.lt(value: V?): LessThan<T, V> =
    LessThan(builder = this, value = value)

/**
 * Equivalent to `<` in SQL
 */
inline infix fun <reified T : Any, reified V, VALUE> KProperty1<T, V>.lt(value: VALUE?): LessThan<T, VALUE> =
    LessThan(this.builder(), value)


/**
 * Equivalent to `NOT ($where)` in SQL
 *
 * This just wraps the passed in where clause.
 */
data class Not<T : Any>(private val where: Where<T>) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        val query = where.toSqlQuery(increment)
        return SqlQuery(
            from = query.from,
            where = "NOT (${query.where})",
            parameters = query.parameters,
            bindArgs = query.bindArgs,
            orderBy = query.orderBy
        )
    }

    override fun toScalarSqlValue(): SqlValueFragment {
        val inner = where.toScalarSqlValue()
        return SqlValueFragment(
            sql = "(NOT ${inner.sql})",
            parameters = inner.parameters,
            bindArgs = { inner.bindArgs(this) },
        )
    }
}

/**
 * Equivalent to `NOT ($where)` in SQL
 */
fun <T : Any> not(where: Where<T>): Not<T> = Not(where)

/**
 * Equivalent to `AND` in SQL
 */
data class And<T : Any>(private val left: Where<T>, private val right: Where<T>) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        val leftQuery = left.toSqlQuery(increment * 10)
        val rightQuery = right.toSqlQuery((increment * 10) + 1)
        return SqlQuery(leftQuery, rightQuery, operator = "AND")
    }

    override fun toScalarSqlValue(): SqlValueFragment {
        val l = left.toScalarSqlValue()
        val r = right.toScalarSqlValue()
        return SqlValueFragment(
            sql = "(${l.sql} AND ${r.sql})",
            parameters = l.parameters + r.parameters,
            bindArgs = { l.bindArgs(this); r.bindArgs(this) },
        )
    }
}

/**
 * Equivalent to `AND` in SQL
 */
infix fun <T : Any> Where<T>.and(other: Where<T>): Where<T> = And(this, other)

/**
 * Equivalent to `OR` in SQL
 */
data class Or<T : Any>(private val left: Where<T>, private val right: Where<T>) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        val leftQuery = left.toSqlQuery(increment * 10)
        val rightQuery = right.toSqlQuery((increment * 10) + 1)
        return SqlQuery(leftQuery, rightQuery, operator = "OR")
    }

    override fun toScalarSqlValue(): SqlValueFragment {
        val l = left.toScalarSqlValue()
        val r = right.toScalarSqlValue()
        return SqlValueFragment(
            sql = "(${l.sql} OR ${r.sql})",
            parameters = l.parameters + r.parameters,
            bindArgs = { l.bindArgs(this); r.bindArgs(this) },
        )
    }
}

/**
 * Equivalent to `OR` in SQL
 */
infix fun <T : Any> Where<T>.or(other: Where<T>): Where<T> = Or(this, other)

abstract class Where<T : Any> {
    abstract fun toSqlQuery(increment: Int): SqlQuery

    /**
     * Emits a scalar boolean SQL fragment using `json_extract` (no LATERAL joins).
     * Used when this Where appears inside a CASE expression branch where row-level
     * dispatch is required.
     */
    abstract fun toScalarSqlValue(): SqlValueFragment
}

sealed class OrderBy<T : Any> {
    internal abstract val direction: OrderDirection?
    internal abstract fun toSqlQuery(index: Int): SqlQuery
}

data class JsonPathOrderBy<T : Any> @PublishedApi internal constructor(
    private val builder: JsonPathBuilder<T>,
    /**
     * Sqlite defaults to ASC when not specified
     */
    override val direction: OrderDirection? = null,
) : OrderBy<T>() {
    private val path: String = builder.buildPath()

    override fun toSqlQuery(index: Int): SqlQuery {
        val treeName = "order_$index"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "$treeName.fullkey LIKE ?",
            parameters = 1,
            bindArgs = { bindString(path) },
            orderBy = "$treeName.value ${direction?.value ?: ""}".trimEnd(),
        )
    }
}

data class CaseOrderBy<T : Any> internal constructor(
    private val case: CaseWhen<T>,
    override val direction: OrderDirection? = null,
) : OrderBy<T>() {
    override fun toSqlQuery(index: Int): SqlQuery {
        val frag = case.toSqlValue()
        return SqlQuery(
            from = null,
            where = null,
            parameters = frag.parameters,
            bindArgs = { frag.bindArgs(this) },
            orderBy = "${frag.sql} ${direction?.value ?: ""}".trimEnd(),
        )
    }
}

fun <T : Any> OrderBy(
    builder: JsonPathBuilder<T>,
    direction: OrderDirection? = null,
): OrderBy<T> = JsonPathOrderBy(builder, direction)

inline fun <reified T : Any, reified V> OrderBy(
    property: KProperty1<T, V>,
    direction: OrderDirection? = null,
): OrderBy<T> = JsonPathOrderBy(property.builder(), direction)

fun <T : Any> OrderBy(
    case: CaseWhen<T>,
    direction: OrderDirection? = null,
): OrderBy<T> = CaseOrderBy(case, direction)

enum class OrderDirection(val value: String) {
    ASC(value = "ASC"),
    DESC(value = "DESC")
}

fun List<OrderBy<*>>.toSqlQueries(): List<SqlQuery> {
    if (isEmpty()) return emptyList()
    return mapIndexed { index, orderBy -> orderBy.toSqlQuery(index) }
}

data class SqlQuery(
    val from: String? = null,
    val where: String? = null,
    val parameters: Int = 0,
    val bindArgs: AutoIncrementSqlPreparedStatement.() -> Unit = {},
    val orderBy: String? = null,
) {
    constructor(left: SqlQuery, right: SqlQuery, operator: String) : this(
        from = listOfNotNull(left.from, right.from).joinToString(", "),
        where = "(${left.where} $operator ${right.where})",
        parameters = left.parameters + right.parameters,
        bindArgs = {
            left.bindArgs(this)
            right.bindArgs(this)
        }
    )

    fun identifier(): Int {
        var result = from?.hashCode() ?: 0
        result = 31 * result + (where?.hashCode() ?: 0)
        result = 31 * result + (orderBy?.hashCode() ?: 0)
        return result
    }
}

internal fun List<SqlQuery>.buildFrom(prefix: String = ", ") = mapNotNull { it.from }
    .joinToString(", ") { it }
    .let { if (it.isNotBlank()) "$prefix$it" else "" }

internal fun List<SqlQuery>.buildWhere(prefix: String = "WHERE") = mapNotNull { it.where }
    .joinToString(" AND ") { it }
    .let { if (it.isNotBlank()) "$prefix $it" else "" }

internal fun List<SqlQuery>.buildOrderBy(prefix: String = "ORDER BY") = mapNotNull { it.orderBy }
    .joinToString(", ") { it }
    .let { if (it.isNotBlank()) "$prefix $it" else "" }


internal fun List<SqlQuery>.sumParameters(): Int = sumOf { it.parameters }

internal fun List<SqlQuery>.identifier(): Int = fold(0) { acc, sqlQuery ->
    31 * acc + sqlQuery.identifier()
}

private enum class CaseOp(val sql: String) { EQ("="), NEQ("!="), GT(">"), LT("<") }
private enum class CaseNullOp(val sql: String) { IS_NULL("IS NULL"), IS_NOT_NULL("IS NOT NULL") }

private fun <T : Any, V> caseCompare(
    case: CaseWhen<T>,
    op: CaseOp,
    value: V?,
): Where<T> = object : Where<T>() {
    private fun fragment(): SqlValueFragment {
        val frag = case.toSqlValue()
        return SqlValueFragment(
            sql = "(${frag.sql} ${op.sql} ?)",
            parameters = frag.parameters + 1,
            bindArgs = {
                frag.bindArgs(this)
                bindValue(value)
            },
        )
    }

    override fun toSqlQuery(increment: Int): SqlQuery {
        val frag = fragment()
        return SqlQuery(
            from = null,
            where = frag.sql,
            parameters = frag.parameters,
            bindArgs = frag.bindArgs,
        )
    }

    override fun toScalarSqlValue(): SqlValueFragment = fragment()
}

private fun <T : Any> caseUnary(
    case: CaseWhen<T>,
    op: CaseNullOp,
): Where<T> = object : Where<T>() {
    private fun fragment(): SqlValueFragment {
        val frag = case.toSqlValue()
        return SqlValueFragment(
            sql = "(${frag.sql} ${op.sql})",
            parameters = frag.parameters,
            bindArgs = { frag.bindArgs(this) },
        )
    }

    override fun toSqlQuery(increment: Int): SqlQuery {
        val frag = fragment()
        return SqlQuery(
            from = null,
            where = frag.sql,
            parameters = frag.parameters,
            bindArgs = frag.bindArgs,
        )
    }

    override fun toScalarSqlValue(): SqlValueFragment = fragment()
}

infix fun <T : Any, V> CaseWhen<T>.eq(value: V?): Where<T> = caseCompare(this, CaseOp.EQ, value)
infix fun <T : Any, V> CaseWhen<T>.neq(value: V?): Where<T> = caseCompare(this, CaseOp.NEQ, value)
infix fun <T : Any, V> CaseWhen<T>.gt(value: V?): Where<T> = caseCompare(this, CaseOp.GT, value)
infix fun <T : Any, V> CaseWhen<T>.lt(value: V?): Where<T> = caseCompare(this, CaseOp.LT, value)

fun <T : Any> CaseWhen<T>.isNull(): Where<T> = caseUnary(this, CaseNullOp.IS_NULL)
fun <T : Any> CaseWhen<T>.isNotNull(): Where<T> = caseUnary(this, CaseNullOp.IS_NOT_NULL)

class AutoIncrementSqlPreparedStatement(
    private var index: Int = 0,
    private val preparedStatement: SqlPreparedStatement,
) {
    fun bindBoolean(boolean: Boolean?) {
        preparedStatement.bindBoolean(index, boolean)
        index++
    }

    fun bindBytes(bytes: ByteArray?) {
        preparedStatement.bindBytes(index, bytes)
        index++
    }

    fun bindDouble(double: Double?) {
        preparedStatement.bindDouble(index, double)
        index++
    }

    fun bindLong(long: Long?) {
        preparedStatement.bindLong(index, long)
        index++
    }

    fun bindString(string: String?) {
        preparedStatement.bindString(index, string)
        index++
    }

    fun <T> bindValue(value: T?) {
        when (value) {
            is Boolean -> bindBoolean(value)
            is ByteArray -> bindBytes(value)
            is Double -> bindDouble(value)
            is Number -> bindLong(value.toLong())
            is String -> bindString(value)
            is Enum<*> -> {
                // Doesn't support @SerialName for now https://github.com/Kotlin/kotlinx.serialization/issues/2956
//                val e = value as T
//                e::class.serializerOrNull()?.let {
//                    val sName = it.descriptor.getElementDescriptor(value.ordinal).serialName
//                    bindString(sName)
//                } ?: bindString(null)
                bindString(value.name) // use ordinal name for now (which is default serialization)
            }

            null -> bindString(null)
            else -> {
                // Compiler bug doesn't smart cast the value to non-null
                val v = requireNotNull(value) { "Unsupported value type: null" }
                throw IllegalArgumentException("Unsupported value type: ${v::class.simpleName}")
            }
        }
    }
}
