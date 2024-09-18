package com.mercury.sqkon.db.serialization

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

/**
 * Default serialization for KeyValueStorage. Uses kotlinx.serialization.Json for serialization.
 */
@OptIn(InternalSerializationApi::class)
class KotlinSqkonSerializer(
    val json: Json = SqkonJson {}
) : SqkonSerializer {
    override fun <T : Any> serialize(klazz: KClass<T>, value: T?): String? {
        value ?: return null
        return json.encodeToString(klazz.serializer(), value)
    }

    override fun <T : Any> deserialize(klazz: KClass<T>, value: String?): T? {
        value ?: return null
        return json.decodeFromString(klazz.serializer(), value)
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
 */
fun SqkonJson(builder: JsonBuilder.() -> Unit) = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    builder()
}
