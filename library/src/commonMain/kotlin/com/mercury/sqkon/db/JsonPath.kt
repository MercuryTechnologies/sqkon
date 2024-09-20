package com.mercury.sqkon.db

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.typeOf

class JsonPathBuilder<R : Any?>
@PublishedApi internal constructor(
    @PublishedApi internal val receiverDescriptor: SerialDescriptor,
) {

    @PublishedApi
    internal var parentNode: JsonPathNode<R, *>? = null

    @PublishedApi
    internal inline fun <reified V : Any?> with(
        property: KProperty1<R, V>,
        block: JsonPathNode<R, V>.() -> Unit = {}
    ): JsonPathBuilder<R> {
        parentNode = JsonPathNode<R, V>(
            //parent = null,
            propertyName = property.name,
            receiverDescriptor = receiverDescriptor,
            valueDescriptor = serializer<V>().descriptor
        ).also(block)
        return this
    }

    // Handles collection property type and extracts the element type vs the list type
    @PublishedApi
    internal inline fun <reified V : Any?> withList(
        property: KProperty1<R, Collection<V>>,
        block: JsonPathNode<R, V>.() -> Unit = {}
    ): JsonPathBuilder<R> {
        parentNode = JsonPathNode<R, V>(
            //parent = null,
            propertyName = property.name,
            receiverDescriptor = receiverDescriptor,
            valueDescriptor = serializer<Collection<V>>().descriptor
        ).also(block)
        return this
    }


    private fun nodes(): List<JsonPathNode<*, *>> {
        val nodes = mutableListOf<JsonPathNode<*, *>>()
        var node: JsonPathNode<*, *>? = parentNode
        while (node != null) {
            nodes.add(node)
            node = node.child
        }
        return nodes.filterInlineClasses()
    }

    private fun List<JsonPathNode<*, *>>.filterInlineClasses(): List<JsonPathNode<*, *>> {
        return this.filter { node -> return@filter !node.receiverDescriptor.isInline }
    }

    fun fieldNames(): List<String> {
        // TOOD: support @SerialName as the serialized name in the db would be different
        return nodes().map { it.propertyName }
    }

    fun buildPath(): String {
        val nodes = nodes()
        // TODO add support for lists
        return nodes.joinToString(".", prefix = "\$.") { it.propertyName }
    }
}

// Builder Methods to start building paths

inline fun <reified R : Any?, reified V : Any?> KProperty1<R, V>.builder(
    block: JsonPathNode<R, V>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>(receiverDescriptor = serializer<R>().descriptor)
        .with(property = this, block = block)
}

inline fun <reified R : Any, reified V : Any> KProperty1<R, Collection<V>>.builderFromList(
    block: JsonPathNode<R, V>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>(receiverDescriptor = serializer(typeOf<R>()).descriptor)
        .withList(property = this, block = block)
}

inline fun <reified R : Any?, reified V : Any?, reified V2 : Any?> KProperty1<R, V>.then(
    property: KProperty1<V, V2>,
    block: JsonPathNode<V, V2>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>(receiverDescriptor = serializer<R>().descriptor)
        .with(property = this) {
            then<V2>(property = property, block = block)
        }
}

inline fun <reified R : Any?, reified V : Any?, reified V2 : Any?> KProperty1<R, Collection<V>>.thenFromList(
    property: KProperty1<V, V2>,
    block: JsonPathNode<V, V2>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>(receiverDescriptor = serializer<R>().descriptor)
        .withList<V>(property = this) {
            then(property = property, block = block)
        }
}


inline fun <reified R : Any?, reified V : Any?, reified V2 : Any?> KProperty1<R, V>.thenList(
    property: KProperty1<V, Collection<V2>>,
    block: JsonPathNode<V, V2>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>(receiverDescriptor = serializer<R>().descriptor)
        .with(property = this) {
            thenList(property = property, block = block)
        }
}

@Suppress("UnusedReceiverParameter")
inline fun <reified R : Any, reified V : Any?> KClass<R>.with(
    property: KProperty1<R, V>,
    block: JsonPathNode<R, V>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>(receiverDescriptor = serializer(typeOf<R>()).descriptor)
        .with(property = property, block = block)
}

// Handles collection property type
@Suppress("UnusedReceiverParameter")
inline fun <reified R : Any, reified V : Any?> KClass<R>.withList(
    property: KProperty1<R, Collection<V>>,
    block: JsonPathNode<R, V>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>(receiverDescriptor = serializer(typeOf<R>()).descriptor)
        .withList(property = property, block = block)
}

/**
 * Represents a path in a JSON object, using limited reflection and descriptors to build the path.
 *
 * Start building using [with] or [withList]
 */
class JsonPathNode<R : Any?, V : Any?>
@PublishedApi
internal constructor(
    //@PublishedApi internal val parent: JsonPathNode<*, R>?,
    val propertyName: String,
    internal val receiverDescriptor: SerialDescriptor,
    @PublishedApi internal val valueDescriptor: SerialDescriptor,
) {

    @PublishedApi
    internal var child: JsonPathNode<V, *>? = null

    inline fun <reified V2 : Any?> then(
        property: KProperty1<V, V2>,
        block: JsonPathNode<V, V2>.() -> Unit = {}
    ): JsonPathNode<R, V> {
        child = JsonPathNode<V, V2>(
            //parent = this,
            propertyName = property.name,
            receiverDescriptor = valueDescriptor,
            valueDescriptor = serializer<V2>().descriptor
        ).also(block)
        return this
    }

    /**
     * Support list, as lists need to be handled differently than object path.
     *
     * This returns the Collection element type, so you can chain into the next property.
     */
    inline fun <reified V2 : Any?> thenList(
        property: KProperty1<V, Collection<V2>>,
        block: JsonPathNode<V, V2>.() -> Unit = {}
    ): JsonPathNode<R, V> {
        child = JsonPathNode<V, V2>(
            //parent = this,
            propertyName = property.name,
            receiverDescriptor = valueDescriptor,
            valueDescriptor = serializer<Collection<V2>>().descriptor
        ).also(block)
        return this
    }
}

private fun testBlock() {
    val pathBuilder: JsonPathBuilder<Test> = Test::class.with(Test::child) {
        then(TestChild::child2) {
            then(TestChild2::childValue2)
        }
    }
    val pathBuilderWithList = Test::class.withList(Test::childList) {
        then(TestChild::childValue)
    }
}

@Deprecated("Use pathBuilder instead", ReplaceWith("pathBuilder()"))
inline infix fun <reified R : Any, reified V : Any, reified V2 : Any> KProperty1<R, V>.then(
    property: KProperty1<V, V2>
): JsonPath<V2> = JsonPath(
    existingPath = listOf(this, property)
)

@Deprecated("Use pathBuilder instead", ReplaceWith("pathBuilder()"))
// Support list type not implemented yet
@JvmName("thenFromList")
inline infix fun <reified R : Any, reified V : Any, reified V2 : Any> KProperty1<R, Collection<V>>.then(
    property: KProperty1<V, V2>
): JsonPath<V2> = JsonPath(
    existingPath = listOf(this, property)
)

/**
 * Represents a path in a JSON object, using limited reflection to build the path.
 *
 * Does not support finding properties in a collection, or value classes
 */
@Deprecated("Use pathBuilder instead", ReplaceWith("pathBuilder()"))
class JsonPath<R : Any?>(
    val existingPath: List<KProperty1<*, *>> = emptyList(),
) {

    internal constructor(property: KProperty1<R, *>) : this(listOf(property))

    @Deprecated("Use pathBuilder instead", ReplaceWith("pathBuilder()"))
    inline infix fun <reified V : Any?> then(property: KProperty1<R, V>): JsonPath<V> {
        return JsonPath(existingPath = existingPath.plus(property))
    }

    @Deprecated("Use pathBuilder instead", ReplaceWith("pathBuilder()"))
    internal fun fieldNames(): List<String> {
        return existingPath.map { it.name }
    }

    @Deprecated("Use pathBuilder instead", ReplaceWith("pathBuilder()"))
    fun build(): String {
        // Right now works for simple objects, we can't do more than ultra basic reflection at KMM
        //  level, so we would need to generate type information for classes we want to build
        //  paths for, or use a different approach
        return fieldNames().joinToString(".", prefix = "\$.")
    }

    override fun toString(): String = build()
}

private data class Test(
    val value: String,
    val child: TestChild,
    val childList: List<TestChild>,
)

private data class TestChild(
    val childValue: String,
    val child2: TestChild2,
)

private data class TestChild2(
    val childValue2: String,
)
