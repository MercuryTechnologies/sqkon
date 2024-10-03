package com.mercury.sqkon.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.mercury.sqkon.db.KeyValueStorage.Config.DeserializePolicy
import com.mercury.sqkon.db.serialization.KotlinSqkonSerializer
import com.mercury.sqkon.db.serialization.SqkonJson
import com.mercury.sqkon.db.serialization.SqkonSerializer
import com.mercury.sqkon.db.utils.nowMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Base interaction to the database.
 *
 * @param serializer if providing your own, recommend using [SqkonJson] to make sure you create
 *  fields consistently.
 */
open class KeyValueStorage<T : Any>(
    protected val entityName: String,
    protected val entityQueries: EntityQueries,
    protected val scope: CoroutineScope,
    protected val type: KType,
    protected val serializer: SqkonSerializer = KotlinSqkonSerializer(),
    protected val config: Config = Config(),
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
            value_ = serializer.serialize(type, value) ?: error("Failed to serialize value")
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
            value = serializer.serialize(type, value) ?: error("Failed to serialize value")
        )
    }

    suspend fun updateAll(values: Map<String, T>) {
        entityQueries.transaction { values.forEach { (key, value) -> update(key, value) } }
    }

    /**
     * Select all rows. Effectively an alias for [select] with no where set.
     */
    fun selectAll(orderBy: List<OrderBy<T>> = emptyList()): Flow<List<T>> {
        return select(where = null, orderBy = orderBy)
    }

    /**
     * Select by key.
     *
     * Note, using where will be less performant than selecting by key.
     */
    fun selectByKey(key: String): Flow<T?> {
        return entityQueries
            .select(
                entityName = entityName,
                entityKey = key,
            )
            .asFlow()
            .mapToOneOrNull(config.dispatcher)
            .map { it.deserialize() }
    }

    fun select(
        where: Where<T>? = null,
        orderBy: List<OrderBy<T>> = emptyList(),
    ): Flow<List<T>> {
        return entityQueries
            .select(
                entityName,
                where = where,
                orderBy = orderBy,
            )
            .asFlow()
            .mapToList(config.dispatcher)
            .map { list ->
                if (list.isEmpty()) return@map emptyList<T>()
                list.mapNotNull { entity -> entity.deserialize() }
            }
    }

    /**
     * Delete all rows. Basically an alias for [delete] with no where set.
     */
    suspend fun deleteAll() = delete(where = null)

    /**
     * Delete by key.
     *
     * If you need to delete all rows, use [deleteAll].
     * If you need to specify which rows to delete, use [delete] with a [Where]. Note, using where
     * will be less performant than deleting by key.
     *
     * @see delete
     * @see deleteAll
     */
    suspend fun deleteByKey(key: String) {
        entityQueries.delete(entityName, entityKey = key)
    }

    /**
     * Delete using where clause. If where is null, all rows will be deleted.
     *
     * Note, it will always be more performant to delete by key, than using where clause pointing
     * at your entities id.
     *
     * @see deleteAll
     * @see deleteByKey
     */
    suspend fun delete(
        where: Where<T>? = null
    ) {
        entityQueries.delete(entityName, where = where)
    }

    fun count(): Flow<Long> {
        return entityQueries.count(entityName)
            .asFlow()
            .mapToOne(config.dispatcher)
    }

    private fun <T : Any> Entity?.deserialize(): T? {
        this ?: return null
        return try {
            println("value_: ${this.value_}")
            serializer.deserialize(type, value_) ?: error("Failed to deserialize value")
        } catch (e: Exception) {
            when (config.deserializePolicy) {
                DeserializePolicy.ERROR -> throw e
                DeserializePolicy.DELETE -> {
                    scope.launch { deleteByKey(entity_key) }
                    null
                }
            }
        }
    }

    data class Config(
        val deserializePolicy: DeserializePolicy = DeserializePolicy.ERROR,
        val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) {
        enum class DeserializePolicy {
            /**
             * Will throw an error if the value can't be deserialized.
             * It is up to you do handle and recover from the error.
             */
            ERROR,

            /**
             * Will delete and return null if the value can't be deserialized.
             */
            DELETE,
        }
    }

}

/**
 * @param serializer if providing your own, recommend using [SqkonJson]  builder.
 */
inline fun <reified T : Any> keyValueStorage(
    entityName: String,
    entityQueries: EntityQueries,
    scope: CoroutineScope,
    serializer: SqkonSerializer = KotlinSqkonSerializer(),
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
): KeyValueStorage<T> {
    return KeyValueStorage(
        entityName = entityName,
        entityQueries = entityQueries,
        scope = scope,
        type = typeOf<T>(),
        serializer = serializer,
        config = config,
    )
}
