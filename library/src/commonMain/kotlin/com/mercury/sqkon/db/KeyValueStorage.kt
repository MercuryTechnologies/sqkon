package com.mercury.sqkon.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.serializer

/**
 * @param json if providing your own, recommend using [SqkonJson]  builder.
 */
open class KeyValueStorage<T : Any>(
    protected val entityName: String,
    protected val entityQueries: EntityQueries,
    protected val json: Json = SqkonJson {},
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

    suspend fun update(key: String, value: T) {
        entityQueries.updateEntity(
            entityName = entityName,
            entityKey = key,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
            expiresAt = null,
            value = json.encodeToString(serializer, value)
        )
    }

    fun selectByKey(key: String): Flow<T?> {
        return entityQueries
            .selectAll(
                entityName = entityName,
                entityKey = key,
                mapper = { json.decodeFromString(serializer, it) },
            )
            .asFlow()
            .mapToOneOrNull(dispatcher)
    }

    fun selectAll(
        where: Where? = null,
        orderBy: List<OrderBy> = emptyList(),
    ): Flow<List<T>> {
        return entityQueries
            .selectAll(
                entityName,
                mapper = { json.decodeFromString(serializer, it) },
                where = where,
                orderBy = orderBy,
            )
            .asFlow()
            .mapToList(dispatcher)
    }

}

/**
 * @param json if providing your own, recommend using [SqkonJson]  builder.
 */
@OptIn(InternalSerializationApi::class)
inline fun <reified T : Any> keyValueStorage(
    entityName: String,
    entityQueries: EntityQueries,
    json: Json = SqkonJson {},
): KeyValueStorage<T> {
    return KeyValueStorage(
        entityName = entityName,
        entityQueries = entityQueries,
        json = json,
        serializer = T::class.serializer()
    )
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
