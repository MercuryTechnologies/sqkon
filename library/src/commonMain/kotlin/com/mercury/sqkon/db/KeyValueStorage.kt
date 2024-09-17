package com.mercury.sqkon.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

open class KeyValueStorage<T : Any>(
    protected val entityName: String,
    protected val entityQueries: EntityQueries,
    protected val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    protected val serializer: KSerializer<T>,
    protected val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    // todo expiration
) {

    suspend fun insert(key: String, value: T) {
        val entity = Entity(
            entity_name = entityName,
            entity_key = key,
            added_at = System.currentTimeMillis(),
            updated_at = System.currentTimeMillis(),
            expires_at = 0,
            value_ = json.encodeToString(serializer, value)
        )
        entityQueries.insertEntity(entity)
    }

    suspend fun insertAll(values: Map<String, T>) {
        entityQueries.transaction {
            values.forEach { (key, value) ->
                println("insertAll value: $value")
                insert(key, value)
            }
        }
    }

    fun selectAll(): Flow<List<T>> {
        return entityQueries
            .selectAll(entityName) { json.decodeFromString(serializer, it) }
            .asFlow().mapToList(dispatcher)
    }

}

@OptIn(InternalSerializationApi::class)
inline fun <reified T : Any> keyValueStorage(
    entityName: String,
    entityQueries: EntityQueries,
    json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
): KeyValueStorage<T> {
    return KeyValueStorage(
        entityName = entityName,
        entityQueries = entityQueries,
        json = json,
        serializer = T::class.serializer()
    )
}
