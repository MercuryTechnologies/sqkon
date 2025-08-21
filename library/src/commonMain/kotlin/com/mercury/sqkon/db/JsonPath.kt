package com.mercury.sqkon.db

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Create a Path builder using one of the many reified methods.
 *
 * From a class:
 *
 * ```
 * val builder = TestObject::class.with(TestObject::statuses) {
 *   then(Status::createdAt)
 * }
 * ```
 *
 * From a property:
 *
 * ```
 * val builder = TestObject::statuses.builder {
 *   then(Status::createdAt)
 * }
 * ```
 *
 * Quick joining two properties:
 *
 * ```
 * val builder = TestObject::statuses.then(Status::createdAt) {
 *   // can optionally keep going
 * }
 * ```
 * Classes like [OrderBy] and [Where] operators take [JsonPathBuilder] or [KProperty1] to build
 * the path for sql queries.
 */
class JsonPathBuilder<R : Any>
@PublishedApi internal constructor() {

    @PublishedApi
    internal var parentNode: JsonPathNode<R, *>? = null

    @PublishedApi
    internal inline fun <reified R1 : R, reified V> with(
        property: KProperty1<R, V>,
        serialName: String? = null,
        block: JsonPathNode<R, V>.() -> Unit = {}
    ): JsonPathBuilder<R> {
        parentNode = JsonPathNode<R, V>(
            //parent = null,
            propertyName = serialName ?: property.name,
            receiverDescriptor = serializer<R1>().descriptor,
            valueDescriptor = serializer<V>().descriptor
        ).also(block)
        return this
    }

    @PublishedApi
    internal inline fun <reified R1 : R, reified V> with(
        baseType: KType,
        property: KProperty1<R1, V>,
        serialName: String? = null,
        block: JsonPathNode<R, V>.() -> Unit = {}
    ): JsonPathBuilder<R> {
        parentNode = JsonPathNode<R, V>(
            //parent = null,
            propertyName = serialName ?: property.name,
            receiverBaseDescriptor = if (baseType != typeOf<R1>()) {
                serializer(baseType).descriptor
            } else null,
            receiverDescriptor = serializer<R1>().descriptor,
            valueDescriptor = serializer<V>().descriptor
        ).also(block)
        return this
    }

    // Handles collection property type and extracts the element type vs the list type
    @PublishedApi
    @JvmName("withList")
    internal inline fun <reified R1 : R, reified V : Any?> with(
        property: KProperty1<R, Collection<V>>,
        serialName: String? = null,
        block: JsonPathNode<R, V>.() -> Unit = {}
    ): JsonPathBuilder<R> {
        parentNode = JsonPathNode<R, V>(
            //parent = null,
            propertyName = serialName ?: property.name,
            receiverDescriptor = serializer<R1>().descriptor,
            valueDescriptor = serializer<Collection<V>>().descriptor
        ).also(block)
        return this
    }


    private fun nodes(): List<JsonPathNode<*, *>> {
        val nodes = mutableListOf<JsonPathNode<*, *>>()
        var node: JsonPathNode<*, *>? = parentNode
        while (node != null) {
            // Insert additional node for parent classes incase they are sealed classes
            if (node.receiverBaseDescriptor != null) {
                nodes.add(
                    JsonPathNode<Any, Any>(
                        propertyName = "",
                        receiverDescriptor = node.receiverBaseDescriptor,
                        valueDescriptor = node.receiverBaseDescriptor
                    )
                )
            }
            nodes.add(node)
            node = node.child
        }
        return nodes
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun fieldNames(element: Boolean = false): List<String> {
        return nodes().mapNotNull { it ->
            if (it.receiverDescriptor.isInline) return@mapNotNull null // Skip inline classes
            val prefix = if (it.propertyName.isNotBlank()) "." else ""
            if (element) {
                when (it.valueDescriptor.kind) {
                    StructureKind.LIST -> "$prefix${it.propertyName}[%]"
                    PolymorphicKind.SEALED -> "$prefix${it.propertyName}[1]"
                    else -> "$prefix${it.propertyName}"
                }
            } else {
                "$prefix${it.propertyName}"
            }
        }
    }

    fun buildPath(): String {
        return fieldNames().joinToString("", prefix = "$")
    }
}

// Builder Methods to start building paths

inline fun <reified R : Any, reified V : Any?> KProperty1<R, V>.builder(
    serialName: String? = null,
    block: JsonPathNode<R, V>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>().with<R, V>(property = this, serialName = serialName, block = block)
}

inline fun <reified R : Any, reified V : Any?> KProperty1<R, Collection<V>>.builderFromList(
    block: JsonPathNode<out R, V>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>().with<R, V>(property = this, block = block)
}

inline fun <reified R : Any, reified V : Any?, reified V1 : V, reified V2> KProperty1<R, V>.then(
    property: KProperty1<V1, V2>,
    fromSerialName: String? = null,
    thenSerialName: String? = null,
    block: JsonPathNode<V1, V2>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>()
        .with<R, V>(property = this, serialName = fromSerialName) {
            then<V1, V2>(property = property, serialName = thenSerialName, block = block)
        }
}

@JvmName("thenFromList")
inline fun <reified R : Any, reified V, reified V1 : V, reified V2> KProperty1<R, Collection<V>>.then(
    property: KProperty1<V1, V2>,
    fromSerialName: String? = null,
    thenSerialName: String? = null,
    block: JsonPathNode<V1, V2>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>()
        .with<R, V>(property = this, fromSerialName) {
            then<V1, V2>(property = property, serialName = thenSerialName, block = block)
        }
}


@JvmName("thenList")
inline fun <reified R : Any, reified V, reified V2> KProperty1<R, V>.then(
    property: KProperty1<out V, Collection<V2>>,
    block: JsonPathNode<out V, V2>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>().with<R, V>(property = this) {
        then(property = property, block = block)
    }
}

inline fun <reified R : Any, reified R1 : R, reified V> KClass<R>.with(
    property: KProperty1<R1, V>,
    serialName: String? = null,
    block: JsonPathNode<R, V>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>().with<R1, V>(
        baseType = typeOf<R>(), property = property, block = block, serialName = serialName,
        )
}

// Handles collection property type
@Suppress("UnusedReceiverParameter")
inline fun <reified R : Any, reified V : Any?> KClass<R>.withList(
    property: KProperty1<R, Collection<V>>,
    serialName: String? = null,
    block: JsonPathNode<R, V>.() -> Unit = {}
): JsonPathBuilder<R> {
    return JsonPathBuilder<R>()
        .with<R, V>(property = property, block = block, serialName = serialName)
}

/**
 * Represents a path in a JSON object, using limited reflection and descriptors to build the path.
 *
 * Start building using [with].
 */
class JsonPathNode<R : Any?, V : Any?>
@PublishedApi
internal constructor(
    //@PublishedApi internal val parent: JsonPathNode<*, R>?,
    val propertyName: String,
    internal val receiverBaseDescriptor: SerialDescriptor? = null,
    internal val receiverDescriptor: SerialDescriptor,
    @PublishedApi internal val valueDescriptor: SerialDescriptor,
) {

    @PublishedApi
    internal var child: JsonPathNode<out V, *>? = null

    /**
     * @param serialName we can't detect overridden serial names so if you have `@SerialName` set,
     * then you will need to pass this through here.
     */
    inline fun <reified V1 : V, reified V2 : Any?> then(
        property: KProperty1<V1, V2>,
        serialName: String? = null,
        block: JsonPathNode<V1, V2>.() -> Unit = {}
    ): JsonPathNode<R, V> {
        child = JsonPathNode<V1, V2>(
            //parent = this,
            propertyName = serialName ?: property.name,
            receiverDescriptor = serializer<V1>().descriptor,
            valueDescriptor = serializer<V2>().descriptor
        ).also(block)
        return this
    }

    /**
     * Support list, as lists need to be handled differently than object path.
     *
     * This returns the Collection element type, so you can chain into the next property.
     */
    @JvmName("thenList")
    inline fun <reified V2 : Any?> then(
        property: KProperty1<out V, Collection<V2>>,
        serialName: String? = null,
        block: JsonPathNode<out V, V2>.() -> Unit = {}
    ): JsonPathNode<R, out V> {
        child = JsonPathNode<V, V2>(
            //parent = this,
            propertyName = serialName ?: property.name,
            receiverDescriptor = valueDescriptor,
            valueDescriptor = serializer<Collection<V2>>().descriptor
        ).also(block)
        return this
    }

    override fun toString(): String {
        return "JsonPathNode(propertyName='$propertyName', receiverDescriptor=$receiverDescriptor, valueDescriptor=$valueDescriptor)"
    }
}

//private fun testBlock() {
//    val pathBuilder: JsonPathBuilder<Test> = Test::class.with(Test::child) {
//        then(TestChild::child2) {
//            then(TestChild2::childValue2)
//        }
//    }
//    val pathBuilderWithList = Test::class.withList(Test::childList) {
//        then(TestChild::childValue)
//    }
//}

//private data class Test(
//    val value: String,
//    val child: TestChild,
//    val childList: List<TestChild>,
//)
//
//private data class TestChild(
//    val childValue: String,
//    val child2: TestChild2,
//)
//
//private data class TestChild2(
//    val childValue2: String,
//)
