package com.mercury.sqkon.db

import kotlin.reflect.KProperty1

data class Eq<T : Any?>(
    private val path: JsonPath<T>, private val value: String,
) : Where<T>() {

    constructor(property: KProperty1<T, Any?>, value: String) : this(JsonPath(property), value)

    override fun toSqlString(): String {
        return "jsonb_extract(entity.value,'${path.build()}') = '$value'"
    }
}

infix fun <T : Any?> JsonPath<T>.eq(value: String): Eq<T> = Eq(this, value)
infix fun <T : Any?> KProperty1<T, *>.eq(value: String): Eq<T> = Eq(this, value)


data class GreaterThan<T : Any?>(
    private val path: JsonPath<T>.() -> JsonPath<*>, private val value: String,
) : Where<T>() {

    constructor(property: KProperty1<T, Any?>, value: String) : this({ then(property) }, value)

    override fun toSqlString(): String {
        return "jsonb_extract(entity.value,'${JsonPath<T>().path().build()}') > '$value'"
    }
}

infix fun <T : Any?> KProperty1<T, *>.gt(value: String): GreaterThan<T> = GreaterThan(this, value)

data class LessThan<T : Any?>(
    private val path: JsonPath<T>.() -> JsonPath<*>, private val value: String,
) : Where<T>() {

    constructor(property: KProperty1<T, Any?>, value: String) : this({ then(property) }, value)

    override fun toSqlString(): String {
        return "jsonb_extract(entity.value,'${JsonPath<T>().path().build()}') < '$value'"
    }
}

infix fun <T : Any?> KProperty1<T, *>.lt(value: String): LessThan<T> = LessThan(this, value)

data class And<T : Any>(private val left: Where<T>, private val right: Where<T>) : Where<T>() {
    override fun toSqlString(): String = "(${left.toSqlString()} AND ${right.toSqlString()})"
}

infix fun <T : Any> Where<T>.and(other: Where<T>): Where<T> = And(this, other)

data class Or<T : Any>(private val left: Where<T>, private val right: Where<T>) : Where<T>() {
    override fun toSqlString(): String = "(${left.toSqlString()} OR ${right.toSqlString()})"
}

infix fun <T : Any> Where<T>.or(other: Where<T>): Where<T> = Or<T>(this, other)

abstract class Where<T : Any?> {
    // TODO use prepared statement bindings for the values
    abstract fun toSqlString(): String
    override fun toString(): String = toSqlString()
}

fun Where<*>?.toSqlString(): String {
    this ?: return ""
    return this.toSqlString()
}

// TODO: See if we can type safe the passed in object to make sure we pick the top level class we want
data class OrderBy<T : Any?>(
    /**
     * Sqlite defaults to ASC when not specified
     */
    val direction: OrderDirection? = null,
    private val builder: JsonPath<T>.() -> JsonPath<*>,
) {

    constructor(
        property: KProperty1<T, Any?>,
        direction: OrderDirection? = null,
    ) : this(direction, { then(property) })

    val path: JsonPath<*> = JsonPath<T>().builder()

    fun identifier(): String {
        return "order_by_${path.fieldNames()}${direction?.value?.let { "_$it" }}"
    }
}

fun <T : Any> List<OrderBy<T>>.toSqlString(): String {
    return if (isEmpty()) {
        ""
    } else {
        "ORDER BY ${
            joinToString(", ") {
                "jsonb_extract(entity.value, '${it.path.build()}') ${it.direction?.value ?: ""}".trim()
            }
        }"
    }
}


enum class OrderDirection(val value: String) {
    ASC(value = "ASC"),
    DESC(value = "DESC")
}

internal fun <T : Any> List<OrderBy<T>>.identifier(): String = joinToString("_") { it.identifier() }
