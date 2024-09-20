package com.mercury.sqkon.db.serialization

import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface SqkonSerializer {
    fun <T : Any> serialize(type: KType, value: T?): String?
    fun <T : Any> deserialize(type: KType, value: String?): T?
}

inline fun <reified T : Any> SqkonSerializer.serialize(value: T?): String? {
    return serialize(typeOf<T>(), value)
}

inline fun <reified T : Any> SqkonSerializer.deserialize(value: String?): T? {
    return deserialize(typeOf<T>(), value)
}
