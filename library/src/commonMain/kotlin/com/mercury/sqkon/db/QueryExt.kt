package com.mercury.sqkon.db

import kotlin.reflect.KProperty1

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
