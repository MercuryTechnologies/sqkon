package com.mercury.sqkon.db

import kotlin.reflect.KProperty1

data class Eq<T>(
    private val entityColumn: String,
    private val value: String,
) : Where<T>() {
    constructor(
        vararg entityColumn: KProperty1<*, *>,
        value: String,
    ) : this(
        entityColumn.joinToString(".") { it.name },
        value
    )

    override fun toSqlString(): String {
        return "jsonb_extract(entity.value,'\$.${entityColumn}') = '$value'"
    }
}

//infix fun <T : Any, V : Any, V2 : Any> KProperty1<T, V>.then(property: KProperty1<V, V2>): KProperty1<V, V2> {
//    // Add to builder
//    return property
//}
//
//fun block() {
//    Test::child then TestChild::childValue
//}
//
//data class Test(
//    val value: String,
//    val child: TestChild,
//)
//
//data class TestChild(
//    val childValue: String,
//)

data class And<T : Any>(private val left: Where<T>, private val right: Where<T>) : Where<T>() {
    override fun toSqlString(): String {
        return "(${left.toSqlString()} AND ${right.toSqlString()})"
    }
}

data class Or<T : Any>(private val left: Where<T>, private val right: Where<T>) : Where<T>() {
    override fun toSqlString(): String {
        return "(${left.toSqlString()} OR ${right.toSqlString()})"
    }
}

infix fun <T : Any> Where<T>.and(other: Where<T>): Where<T> {
    return And(this, other)
}

infix fun <T : Any> Where<T>.or(other: Where<T>): Where<T> {
    return Or<T>(this, other)
}

abstract class Where<T> {
    // TODO use prepared statement bindings for the values
    abstract fun toSqlString(): String
    override fun toString(): String = toSqlString()
}

fun Where<*>?.toSqlString(): String {
    this ?: return ""
    return this.toSqlString()
}

// TODO: See if we can type safe the passed in object to make sure we pick the top level class we want
data class OrderBy(
    val entityColumn: String,
    /**
     * Sqlite defaults to ASC when not specified
     */
    val direction: OrderDirection? = null,
) {
    // TODO parse fields better
    constructor(
        vararg entityColumn: KProperty1<*, *>,
        direction: OrderDirection? = null
    ) : this(
        entityColumn.joinToString(".") { it.name },
        direction
    )

    fun identifier(): String {
        return "order_by_${entityColumn}${direction?.value?.let { "_$it" }}"
    }
}

fun List<OrderBy>.toSqlString(): String {
    return if (isEmpty()) {
        ""
    } else {
        "ORDER BY ${
            joinToString(", ") {
                "jsonb_extract(entity.value, '\$.${it.entityColumn}') ${it.direction?.value ?: ""}".trim()
            }
        }"
    }
}


enum class OrderDirection(val value: String) {
    ASC(value = "ASC"),
    DESC(value = "DESC")
}

internal fun List<OrderBy>.identifier(): String = joinToString("_") { it.identifier() }
