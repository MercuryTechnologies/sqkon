package com.mercury.sqkon.db

import kotlin.reflect.KProperty1

data class Eq(
    private val entityColumn: String,
    private val value: String,
) : Where() {
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

data class And(private val left: Where, private val right: Where) : Where() {
    override fun toSqlString(): String {
        return "(${left.toSqlString()} AND ${right.toSqlString()})"
    }
}

data class Or(private val left: Where, private val right: Where) : Where() {
    override fun toSqlString(): String {
        return "(${left.toSqlString()} OR ${right.toSqlString()})"
    }
}

infix fun Where.and(other: Where): Where {
    return And(this, other)
}

infix fun Where.or(other: Where): Where {
    return Or(this, other)
}

abstract class Where {
    // TODO use prepared statement bindings for the values
    abstract fun toSqlString(): String
    override fun toString(): String = toSqlString()
}

fun Where?.toSqlString(): String {
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
