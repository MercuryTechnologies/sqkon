package com.mercury.sqkon.db

import app.cash.sqldelight.db.SqlPreparedStatement
import kotlin.reflect.KProperty1

data class Eq<T : Any?>(
    private val builder: JsonPathBuilder<T>, private val value: String?,
) : Where<T>() {
    override fun toSqlString(keyColumn: String, valueColumn: String): String {
        return "($keyColumn like '${builder.buildPath()}' AND $valueColumn = '$value')"
    }

    override fun identifier(): String = "${builder.fieldNames().joinToString()}eq"
}

infix fun <T : Any?> JsonPathBuilder<T>.eq(value: String?): Eq<T> =
    Eq(builder = this, value = value)

inline infix fun <reified T, reified V> KProperty1<T, V>.eq(value: String?): Eq<T> =
    Eq(this.builder(), value)


data class GreaterThan<T : Any?>(
    private val builder: JsonPathBuilder<T>, private val value: String?,
) : Where<T>() {
    override fun toSqlString(keyColumn: String, valueColumn: String): String {
        return "($keyColumn like '${builder.buildPath()}' AND $valueColumn > '$value')"
    }

    override fun identifier(): String = "${builder.fieldNames().joinToString()}gt"
}

infix fun <T> JsonPathBuilder<T>.gt(value: String?): GreaterThan<T> =
    GreaterThan(builder = this, value = value)

inline infix fun <reified T, reified V> KProperty1<T, V>.gt(value: String?): GreaterThan<T> =
    GreaterThan(this.builder(), value)


data class LessThan<T : Any?>(
    private val builder: JsonPathBuilder<T>, private val value: String?,
) : Where<T>() {
    override fun toSqlString(keyColumn: String, valueColumn: String): String {
        return "($keyColumn LIKE '${builder.buildPath()}' AND $valueColumn < '$value')"
    }

    override fun identifier(): String = "${builder.fieldNames().joinToString()}lt"
}

infix fun <T> JsonPathBuilder<T>.lt(value: String?): LessThan<T> =
    LessThan(builder = this, value = value)

inline infix fun <reified T, reified V> KProperty1<T, V>.lt(value: String?): LessThan<T> =
    LessThan(this.builder(), value)

data class And<T : Any>(private val left: Where<T>, private val right: Where<T>) : Where<T>() {
    override fun toSqlString(keyColumn: String, valueColumn: String): String {
        return "(${left.toSqlString(keyColumn, valueColumn)} " +
                "AND ${right.toSqlString(keyColumn, valueColumn)})"
    }

    override fun identifier(): String = "${left.identifier()}and${right.identifier()}"
}

infix fun <T : Any> Where<T>.and(other: Where<T>): Where<T> = And(this, other)

data class Or<T : Any>(private val left: Where<T>, private val right: Where<T>) : Where<T>() {
    override fun toSqlString(keyColumn: String, valueColumn: String): String {
        return "(${left.toSqlString(keyColumn, valueColumn)} " +
                "OR ${right.toSqlString(keyColumn, valueColumn)})"
    }

    override fun identifier(): String = "${left.identifier()}or${right.identifier()}"
}

infix fun <T : Any> Where<T>.or(other: Where<T>): Where<T> = Or(this, other)

abstract class Where<T : Any?> {
    // TODO use prepared statement bindings for the values
    abstract fun toSqlString(keyColumn: String, valueColumn: String): String
    abstract fun identifier(): String
    override fun toString(): String = toSqlString(keyColumn = "*", valueColumn = "*")
}

fun Where<*>?.toSqlString(keyColumn: String, valueColumn: String): String {
    this ?: return ""
    return this.toSqlString(keyColumn, valueColumn)
}

fun Where<*>?.identifier(): String? {
    this ?: return null
    return this.identifier()
}

data class OrderBy<T>(
    private val builder: JsonPathBuilder<T>,
    /**
     * Sqlite defaults to ASC when not specified
     */
    internal val direction: OrderDirection? = null,
) {
    val path: String = builder.buildPath()

    fun identifier(): String {
        return "order_by_${builder.fieldNames()}${direction?.value?.let { "_$it" }}"
    }
}

inline fun <reified T, reified V> OrderBy(
    property: KProperty1<T, V>, direction: OrderDirection? = null,
) = OrderBy(property.builder(), direction)

fun <T : Any> List<OrderBy<T>>.toSqlQuery(): List<SqlQuery> {
    if (isEmpty()) return emptyList()
    return mapIndexed { index, orderBy ->
        val treeName = "order_$index"
        SqlQuery(
            from = "json_tree(entity.value, '$') as $treeName",
            where = "$treeName.fullkey LIKE ?",
            bindArgs = {
                bindString(orderBy.path)
            },
            orderBy = "$treeName.value ${orderBy.direction?.value ?: ""}",
        )
    }
}


enum class OrderDirection(val value: String) {
    ASC(value = "ASC"),
    DESC(value = "DESC")
}

internal fun <T : Any> List<OrderBy<T>>.identifier(): String = joinToString("_") { it.identifier() }

data class SqlQuery(
    val from: String? = null,
    val where: String? = null,
    val bindArgs: AutoIncrementSqlPreparedStatement.() -> Unit = {},
    val orderBy: String? = null,
)

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
}
