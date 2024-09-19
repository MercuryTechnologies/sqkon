package com.mercury.sqkon.db

import kotlin.reflect.KProperty1

inline infix fun <reified R : Any, reified V : Any, reified V2 : Any> KProperty1<R, V>.then(
    property: KProperty1<V, V2>
): JsonPath<V2> = JsonPath(
    existingPath = listOf(this, property)
)

/**
 * Represents a path in a JSON object, using limited reflection to build the path.
 *
 * Does not support finding properties in a collection, or value classes
 */
class JsonPath<R : Any?>(
    val existingPath: List<KProperty1<*, *>> = emptyList(),
) {

    internal constructor(property: KProperty1<R, *>) : this(listOf(property))

    inline infix fun <reified V : Any?> then(property: KProperty1<R, V>): JsonPath<V> {
        return JsonPath(existingPath = existingPath.plus(property))
    }

    internal fun fieldNames(): List<String> {
        return existingPath.map { it.name }
    }

    fun build(): String {
        // Right now works for simple objects, we can't do more than ultra basic reflection at KMM
        //  level, so we would need to generate type information for classes we want to build
        //  paths for, or use a different approach
        return fieldNames().joinToString(".", prefix = "\$.")
    }

    override fun toString(): String = build()
}

private fun block() {
    Test::child then TestChild::childValue
    Test::child then TestChild::child2 then TestChild2::childValue2
}

private data class Test(
    val value: String,
    val child: TestChild,
)

private data class TestChild(
    val childValue: String,
    val child2: TestChild2,
)

private data class TestChild2(
    val childValue2: String,
)
