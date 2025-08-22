package com.mercury.sqkon.db

import app.cash.sqldelight.db.SqlPreparedStatement
import com.mercury.sqkon.db.serialization.SqkonSerializer
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@PublishedApi
internal val AnyType = typeOf<Any>()

// FIXME This is not ideal, but KProperty1<T, out V> doesn't have the right
//   variance to prevent Any from satisfying V.
/**
 * Check that we aren't coercing types to Any at runtime.
 */
@PublishedApi
internal inline fun <reified T> checkedTypeOf(): KType {
    val type = typeOf<T>()
    if (type == AnyType) {
        throw IllegalArgumentException(
            "Type was coerced to Any; did you pass the correct value?"
        )
    }
    return type
}

/**
 * Equivalent to `=` in SQL
 */
data class Eq<T : Any, V : Any?>(
    private val builder: JsonPathBuilder<T>,
    private val type: KType,
    private val value: V,
) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        val treeName = "eq_$increment"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "($treeName.fullkey LIKE ? AND json_quote($treeName.value) = json_quote(?))",
            parameters = 2,
            bindArgs = {
                bindString(builder.buildPath())
                bindValue(type, value)
            }
        )
    }
}

/**
 * Equivalent to `=` in SQL
 */
inline infix fun <T : Any, reified V> JsonPathBuilder<T>.eq(value: V): Eq<T, V> =
    Eq(builder = this, type = checkedTypeOf<V>(), value = value)

/**
 * Equivalent to `=` in SQL
 */
inline infix fun <reified T : Any, reified V> KProperty1<T, V>.eq(value: V): Eq<T, V> =
    Eq(this.builder(), checkedTypeOf<V>(), value)

/**
 * Equivalent to `!=` in SQL
 */
data class NotEq<T : Any, V>(
    private val builder: JsonPathBuilder<T>,
    private val type: KType,
    private val value: V,
) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        return Not(Eq(builder = builder, type = type, value = value)).toSqlQuery(increment)
    }
}

/**
 * Equivalent to `!=` in SQL
 */
inline infix fun <T : Any, reified V> JsonPathBuilder<T>.neq(value: V): NotEq<T, V> =
    NotEq(builder = this, type = checkedTypeOf<V>(), value = value)

/**
 * Equivalent to `!=` in SQL
 */
inline infix fun <reified T : Any, reified V> KProperty1<T, V>.neq(value: V): NotEq<T, V> =
    NotEq(this.builder(), checkedTypeOf<V>(), value)

/**
 * Equivalent to `IN` in SQL
 */
data class In<T : Any, V>(
    private val builder: JsonPathBuilder<T>,
    private val type: KType,
    private val value: Collection<V>,
) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        val treeName = "in_$increment"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "($treeName.fullkey LIKE ? AND json_quote($treeName.value) IN (${value.joinToString(",") { "json_quote(?)" }}))",
            parameters = 1 + value.size,
            bindArgs = {
                bindString(builder.buildPath())
                value.forEach { bindValue(type, it) }
            }
        )
    }
}

/**
 * Equivalent to `IN` in SQL
 */
inline infix fun <T : Any, reified V> JsonPathBuilder<T>.inList(value: Collection<V>): In<T, V> =
    In(builder = this, type = checkedTypeOf<V>(), value = value)

inline infix fun <reified T : Any, reified V> KProperty1<T, V>.inList(value: Collection<V>): In<T, V> =
    In(this.builder(), checkedTypeOf<V>(), value)

data class Contains<T : Any, V>(
    private val builder: JsonPathBuilder<T>,
    private val type: KType,
    private val value: V,
) : Where<T>() {
    override fun toSqlQuery(increment: Int): SqlQuery {
        val treeName = "contains_$increment"
        val eachName = "contains_${increment}_each"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName, json_each($treeName.value) as $eachName",
            where = "($treeName.fullkey LIKE ? AND json_quote($eachName.value) = json_quote(?))",
            parameters = 2,
            bindArgs = {
                bindString(builder.buildPath())
                bindValue(type, value)
            }
        )
    }
}

inline infix fun <T : Any, reified V> JsonPathBuilder<T>.contains(value: V): Contains<T, V> =
    Contains(builder = this, type = checkedTypeOf<V>(), value = value)

inline infix fun <reified T : Any, reified V, reified C : Collection<V>?> KProperty1<T, C>.contains(value: V): Contains<T, V> =
    Contains(this.builder(), checkedTypeOf<V>(), value)

/**
 * Equivalent to `LIKE` in SQL
 */
data class Like<T : Any>(
    private val builder: JsonPathBuilder<T>,
    private val value: String,
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
}

/**
 * Equivalent to `LIKE` in SQL
 */
infix fun <T : Any> JsonPathBuilder<T>.like(value: String): Like<T> =
    Like(builder = this, value = value)

/**
 * Equivalent to `LIKE` in SQL
 */
inline infix fun <reified T : Any, reified V> KProperty1<T, V>.like(value: String): Like<T> =
    Like(this.builder(), value)

/**
 * Equivalent to `>` in SQL
 *
 * @param value note that gt will only really work with numbers right now.
 */
data class GreaterThan<T : Any, V>(
    private val builder: JsonPathBuilder<T>,
    private val type: KType,
    private val value: V?,
) : Where<T>() {

    override fun toSqlQuery(increment: Int): SqlQuery {
        val treeName = "gt_$increment"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "($treeName.fullkey LIKE ? AND json_quote($treeName.value) > json_quote(?))",
            parameters = 2,
            bindArgs = {
                bindString(builder.buildPath())
                bindValue(type, value)
            }
        )
    }
}

/**
 * Equivalent to `>` in SQL
 */
inline infix fun <T : Any, reified V> JsonPathBuilder<T>.gt(value: V): GreaterThan<T, V> =
    GreaterThan(builder = this, type = checkedTypeOf<V>(), value = value)

/**
 * Equivalent to `>` in SQL
 */
inline infix fun <reified T : Any, reified V> KProperty1<T, V>.gt(value: V): GreaterThan<T, V> =
    GreaterThan(this.builder(), checkedTypeOf<V>(), value)


/**
 * Equivalent to `<` in SQL
 */
data class LessThan<T : Any, V>(
    private val builder: JsonPathBuilder<T>,
    private val type: KType,
    private val value: V,
) : Where<T>() {

    override fun toSqlQuery(increment: Int): SqlQuery {
        val treeName = "lt_$increment"
        return SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "($treeName.fullkey LIKE ? AND json_quote($treeName.value) < json_quote(?))",
            parameters = 2,
            bindArgs = {
                bindString(builder.buildPath())
                bindValue(type, value)
            }
        )
    }
}

/**
 * Equivalent to `<` in SQL
 */
inline infix fun <T : Any, reified V> JsonPathBuilder<T>.lt(value: V): LessThan<T, V> =
    LessThan(builder = this, type = checkedTypeOf<V>(), value = value)

/**
 * Equivalent to `<` in SQL
 */
inline infix fun <reified T : Any, reified V> KProperty1<T, V>.lt(value: V): LessThan<T, V> =
    LessThan(this.builder(), checkedTypeOf<V>(), value)

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
}

/**
 * Equivalent to `OR` in SQL
 */
infix fun <T : Any> Where<T>.or(other: Where<T>): Where<T> = Or(this, other)

abstract class Where<T : Any> {
    abstract fun toSqlQuery(increment: Int): SqlQuery
}

data class OrderBy<T : Any>(
    private val builder: JsonPathBuilder<T>,
    /**
     * Sqlite defaults to ASC when not specified
     */
    internal val direction: OrderDirection? = null,
) {
    val path: String = builder.buildPath()
}

inline fun <reified T : Any, reified V> OrderBy(
    property: KProperty1<T, V>, direction: OrderDirection? = null,
) = OrderBy(property.builder(), direction)

enum class OrderDirection(val value: String) {
    ASC(value = "ASC"),
    DESC(value = "DESC")
}

fun List<OrderBy<*>>.toSqlQueries(): List<SqlQuery> {
    if (isEmpty()) return emptyList()
    return mapIndexed { index, orderBy ->
        val treeName = "order_$index"
        SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "$treeName.fullkey LIKE ?",
            parameters = 1,
            bindArgs = { bindString(orderBy.path) },
            orderBy = "$treeName.value ${orderBy.direction?.value ?: ""}",
        )
    }
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

class AutoIncrementSqlPreparedStatement(
    private var index: Int = 0,
    private val preparedStatement: SqlPreparedStatement,
    private val serializer: SqkonSerializer,
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

    fun <T> bindValue(type: KType, value: T) {
        when (value) {
            is Boolean -> bindBoolean(value)
            is ByteArray -> bindBytes(value)
            is Double -> bindDouble(value)
            is Long -> bindLong(value)
            is String -> bindString(value)
            else -> bindString(serializer.serialize(type, value))
        }
    }
}
