package com.mercury.sqkon.db.utils

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.serializer
import kotlin.reflect.KProperty

/**
 * Utility function to get the serialized name for a property.
 * Uses the generated property indices map at runtime.
 *
 * TODO make expect/actual for JS/iOS etc
 */
@OptIn(InternalSerializationApi::class)
internal inline fun <reified T : Any> KProperty<*>.serialName(): String {
    // Get the descriptor for the type
    val descriptor: SerialDescriptor = T::class.serializer().descriptor

    // Get the companion object for the class
    val companion = T::class.java.companionOrNull("Companion")

    // Get the property indices map from the companion
    @Suppress("UNCHECKED_CAST")
    val propertyIndices = companion?.let {
        try {
            val method = it::class.java.getMethod("getPropertyIndices")
            method.invoke(it) as Map<String, Int>
        } catch (e: Exception) {
            null
        }
    }

    // Get the index for this property
    val index = propertyIndices?.get(name)

    // Use the index to get the serialized name from the descriptor
    return if (index != null && index in 0 until descriptor.elementsCount) {
        descriptor.getElementName(index)
    } else {
        // Fallback to property name
        name
    }
}

private fun Class<*>.companionOrNull(companionName: String) =
    try {
        val companion = getDeclaredField(companionName)
        companion.isAccessible = true
        companion.get(null)
    } catch (e: Throwable) {
        null
    }
