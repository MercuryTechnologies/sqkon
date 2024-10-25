package com.mercury.sqkon.db.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.serializer
import kotlin.reflect.KType

/**
 * Default serialization for KeyValueStorage. Uses kotlinx.serialization.Json for serialization.
 */
class KotlinSqkonSerializer(
    val json: Json = SqkonJson {}
) : SqkonSerializer {
    override fun <T : Any> serialize(type: KType, value: T?): String? {
        value ?: return null
        return json.encodeToString(serializer(type), value)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> deserialize(type: KType, value: String?): T? {
        value ?: return null
        return json.decodeFromString(serializer(type), value) as T
    }
}


/**
 * Recommended default json configuration for KeyValueStorage.
 *
 * This configuration generally allows changes to json and enables ordering and querying.
 *
 * - `ignoreUnknownKeys = true` is generally recommended to allow for removing fields from classes.
 * - `encodeDefaults` = true, is required to be able to query on default values, otherwise that field
 *     is missing in the db.
 * - `useArrayPolymorphism = true` is required for polymorphic serialization when you use value
 *     classes without custom descriptors
 */
fun SqkonJson(builder: JsonBuilder.() -> Unit) = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    // https://github.com/Kotlin/kotlinx.serialization/issues/2049#issuecomment-1271536271
    useArrayPolymorphism = true
    builder()
}
