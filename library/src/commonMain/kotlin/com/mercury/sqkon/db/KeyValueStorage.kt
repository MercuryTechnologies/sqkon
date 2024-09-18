package com.mercury.sqkon.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.mercury.sqkon.db.serialization.KotlinSqkonSerializer
import com.mercury.sqkon.db.serialization.SqkonJson
import com.mercury.sqkon.db.serialization.SqkonSerializer
import com.mercury.sqkon.db.utils.nowMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Base interaction to the database.
 *
 * @param json if providing your own, recommend using [SqkonJson]  builder.
 */
open class KeyValueStorage<T : Any>(
    protected val entityName: String,
    protected val entityQueries: EntityQueries,
    protected val klazz: KClass<T>,
    protected val serializer: SqkonSerializer = KotlinSqkonSerializer(),
    protected val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    // TODO expiresAt
) {

    suspend fun insert(key: String, value: T) {
        val now = nowMillis()
        val entity = Entity(
            entity_name = entityName,
            entity_key = key,
            added_at = now,
            updated_at = now,
            expires_at = null,
            value_ = serializer.serialize(klazz, value) ?: error("Failed to serialize value")
        )
        entityQueries.insertEntity(entity)
    }

    suspend fun insertAll(values: Map<String, T>) {
        entityQueries.transaction {
            values.forEach { (key, value) -> insert(key, value) }
        }
    }

    suspend fun update(key: String, value: T) {
        entityQueries.updateEntity(
            entityName = entityName,
            entityKey = key,
            updatedAt = nowMillis(),
            expiresAt = null,
            value = serializer.serialize(klazz, value) ?: error("Failed to serialize value")
        )
    }

    suspend fun updateAll(values: Map<String, T>) {
        entityQueries.transaction { values.forEach { (key, value) -> update(key, value) } }
    }

    // TODO replace

    fun selectByKey(key: String): Flow<T?> {
        return entityQueries
            .selectAll(
                entityName = entityName,
                entityKey = key,
                mapper = {
                    serializer.deserialize(klazz, it) ?: error("Failed to deserialize value")
                },
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
                mapper = {
                    serializer.deserialize(klazz, it) ?: error("Failed to deserialize value")
                },
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
inline fun <reified T : Any> keyValueStorage(
    entityName: String,
    entityQueries: EntityQueries,
    serializer: SqkonSerializer = KotlinSqkonSerializer(),
): KeyValueStorage<T> {
    return KeyValueStorage(
        entityName = entityName,
        entityQueries = entityQueries,
        klazz = T::class,
        serializer = serializer,
    )
}
