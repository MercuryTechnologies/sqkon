package com.mercury.sqkon.db.serialization

import kotlin.reflect.KClass

interface SqkonSerializer {

    fun <T : Any> serialize(klazz: KClass<T>, value: T?): String?
    fun <T : Any> deserialize(klazz: KClass<T>, value: String?): T?

}

inline fun <reified T : Any> SqkonSerializer.serialize(value: T?): String? {
    return serialize(T::class, value)
}

inline fun <reified T : Any> SqkonSerializer.deserialize(value: String?): T? {
    return deserialize(T::class, value)
}
