package com.mercury.sqkon.db

import app.cash.paging.PagingSource
import app.cash.sqldelight.SuspendingTransacter
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.mercury.sqkon.db.KeyValueStorage.Config.DeserializePolicy
import com.mercury.sqkon.db.paging.OffsetQueryPagingSource
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
) : SuspendingTransacter by entityQueries {

    /**
     * Insert a row.
     *
     * @param ignoreIfExists if true, will not insert if a row with the same key already exists.
     *  Otherwise, throw primary key constraint violation. Useful for "upserting".
     *
     *  @see update
     *  @see upsert
     */
    suspend fun insert(key: String, value: T, ignoreIfExists: Boolean = false) {
        val now = nowMillis()
        val entity = Entity(
            entity_name = entityName,
            entity_key = key,
            added_at = now,
            updated_at = now,
            expires_at = null,
            value_ = serializer.serialize(type, value) ?: error("Failed to serialize value")
        )
        entityQueries.insertEntity(entity, ignoreIfExists)
    }

    /**
     * Insert multiple rows.
     *
     * @param ignoreIfExists if true, will not insert if a row with the same key already exists.
     *
     * @see updateAll
     * @see upsertAll
     */
    suspend fun insertAll(values: Map<String, T>, ignoreIfExists: Boolean = false) {
        transaction {
            values.forEach { (key, value) -> insert(key, value, ignoreIfExists) }
        }
    }

    /**
     * Update a row. If the row does not exist, it will update nothing, use [insert] if you want to
     * insert if the row does not exist.
     *
     * We also provide [upsert] convenience function to insert or update.
     *
     * @see insert
     * @see upsert
     */
    suspend fun update(key: String, value: T) {
        entityQueries.updateEntity(
            entityName = entityName,
            entityKey = key,
            updatedAt = nowMillis(),
            expiresAt = null,
            value = serializer.serialize(type, value) ?: error("Failed to serialize value")
        )
    }

    /**
     * Convenience function to insert collection of rows. If the row does not exist, ti will update
     * nothing, use [insert] if you want to insert if the row does not exist.
     *
     * @see insertAll
     * @see upsertAll
     */
    suspend fun updateAll(values: Map<String, T>) {
        transaction { values.forEach { (key, value) -> update(key, value) } }
    }


    /**
     * Convenience function to insert a new row or update an existing row.
     *
     * @see insert
     * @see update
     */
    suspend fun upsert(key: String, value: T) {
        transaction {
            update(key, value)
            insert(key, value, ignoreIfExists = true)
        }
    }

    /**
     * Convenience function to insert new or update existing multiple rows.
     *
     * Basically an alias for [updateAll] and [insertAll] with ignoreIfExists set to true.
     *
     * @see insertAll
     * @see updateAll
     */
    suspend fun upsertAll(values: Map<String, T>) {
        transaction {
            values.forEach { (key, value) ->
                update(key, value)
                insert(key, value, ignoreIfExists = true)
            }
        }
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
     * Key selection will always be more performant than using where clause. Keys are indexed.
     */
    fun selectByKey(key: String): Flow<T?> {
        return selectByKeys(listOf(key)).map { it.firstOrNull() }
    }

    /**
     * Select by keys with optional ordering
     *
     * Key selection will always be more performant than using where clause. Keys are indexed.
     */
    fun selectByKeys(
        keys: Collection<String>,
        orderBy: List<OrderBy<T>> = emptyList(),
    ): Flow<List<T>> {
        return entityQueries
            .select(
                entityName = entityName,
                entityKeys = keys,
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
     * Select using where clause. If where is null, all rows will be selected.
     *
     * Simple example with where and orderBy:
     * ```
     * val merchantsFlow = store.select(
     *  where = Merchant::category like "Restaurant",
     *  orderBy = listOf(OrderBy(Merchant::createdAt, OrderDirection.DESC))
     * )
     * ```
     */
    fun select(
        where: Where<T>? = null,
        orderBy: List<OrderBy<T>> = emptyList(),
        limit: Long? = null,
        offset: Long? = null,
    ): Flow<List<T>> {
        return entityQueries
            .select(
                entityName,
                where = where,
                orderBy = orderBy,
                limit = limit,
                offset = offset,
            )
            .asFlow()
            .mapToList(config.dispatcher)
            .map { list ->
                if (list.isEmpty()) return@map emptyList<T>()
                list.mapNotNull { entity -> entity.deserialize() }
            }
    }

    /**
     * Create a [PagingSource] that pages through results according to queries generated by from the
     * passed in [where] and [orderBy]. [initialOffset] initial offset to start paging from.
     *
     * Queries will be executed on [Config.dispatcher].
     *
     * Note: Offset Paging is not very efficient on large datasets. Use wisely. We are working
     * on supporting [keyset paging](https://sqldelight.github.io/sqldelight/2.0.2/common/androidx_paging_multiplatform/#keyset-paging) in the future.
     */
    fun selectPagingSource(
        where: Where<T>? = null,
        orderBy: List<OrderBy<T>> = emptyList(),
        initialOffset: Int = 0,
    ): PagingSource<Int, T> = OffsetQueryPagingSource(
        queryProvider = { limit, offset ->
            entityQueries.select(
                entityName,
                where = where,
                orderBy = orderBy,
                limit = limit.toLong(),
                offset = offset.toLong()
            )
        },
        countQuery = entityQueries.count(entityName, where = where),
        transacter = entityQueries,
        context = config.dispatcher,
        deserialize = { it.deserialize() },
        initialOffset = initialOffset,
    )

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
    suspend fun delete(where: Where<T>? = null) {
        entityQueries.delete(entityName, where = where)
    }

    fun count(): Flow<Int> {
        return entityQueries.count(entityName)
            .asFlow()
            .mapToOne(config.dispatcher)
    }

    private fun <T : Any> Entity?.deserialize(): T? {
        this ?: return null
        return try {
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
