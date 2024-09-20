package com.mercury.sqkon.db

import kotlin.reflect.KProperty1

data class Eq<T : Any?>(
    private val builder: JsonPathBuilder<T>, private val value: String?,
) : Where<T>() {
    override fun toSqlString(): String {
        return "jsonb_extract(entity.value,'${builder.buildPath()}') = '$value'"
    }
}

infix fun <T : Any?> JsonPathBuilder<T>.eq(value: String?): Eq<T> =
    Eq(builder = this, value = value)

inline infix fun <reified T, reified V> KProperty1<T, V>.eq(value: String?): Eq<T> =
    Eq(this.builder(), value)


data class GreaterThan<T : Any?>(
    private val builder: JsonPathBuilder<T>, private val value: String?,
) : Where<T>() {

    override fun toSqlString(): String {
        return "jsonb_extract(entity.value,'${builder.buildPath()}') > '$value'"
    }
}

infix fun <T> JsonPathBuilder<T>.gt(value: String?): GreaterThan<T> =
    GreaterThan(builder = this, value = value)

inline infix fun <reified T, reified V> KProperty1<T, V>.gt(value: String?): GreaterThan<T> =
    GreaterThan(this.builder(), value)


data class LessThan<T : Any?>(
    private val builder: JsonPathBuilder<T>, private val value: String?,
) : Where<T>() {

    override fun toSqlString(): String {
        return "jsonb_extract(entity.value,'${builder.buildPath()}') < '$value'"
    }
}

infix fun <T> JsonPathBuilder<T>.lt(value: String?): LessThan<T> =
    LessThan(builder = this, value = value)

inline infix fun <reified T, reified V> KProperty1<T, V>.lt(value: String?): LessThan<T> =
    LessThan(this.builder(), value)

data class And<T : Any>(private val left: Where<T>, private val right: Where<T>) : Where<T>() {
    override fun toSqlString(): String = "(${left.toSqlString()} AND ${right.toSqlString()})"
}

infix fun <T : Any> Where<T>.and(other: Where<T>): Where<T> = And(this, other)

data class Or<T : Any>(private val left: Where<T>, private val right: Where<T>) : Where<T>() {
    override fun toSqlString(): String = "(${left.toSqlString()} OR ${right.toSqlString()})"
}

infix fun <T : Any> Where<T>.or(other: Where<T>): Where<T> = Or(this, other)

abstract class Where<T : Any?> {
    // TODO use prepared statement bindings for the values
    abstract fun toSqlString(): String
    override fun toString(): String = toSqlString()
}

fun Where<*>?.toSqlString(): String {
    this ?: return ""
    return this.toSqlString()
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

fun <T : Any> List<OrderBy<T>>.toSqlString(): String {
    return if (isEmpty()) {
        ""
    } else {
        "ORDER BY ${
            joinToString(", ") {
                "jsonb_extract(entity.value, '${it.path}') ${it.direction?.value ?: ""}".trim()
            }
        }"
    }
}


enum class OrderDirection(val value: String) {
    ASC(value = "ASC"),
    DESC(value = "DESC")
}

internal fun <T : Any> List<OrderBy<T>>.identifier(): String = joinToString("_") { it.identifier() }
