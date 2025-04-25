package com.mercury.sqkon.db

import app.cash.paging.PagingSource
import app.cash.sqldelight.SuspendingTransacter
import app.cash.sqldelight.SuspendingTransactionWithReturn
import app.cash.sqldelight.SuspendingTransactionWithoutReturn
import app.cash.sqldelight.TransactionCallbacks
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneNotNull
import com.mercury.sqkon.db.KeyValueStorage.Config.DeserializePolicy
import com.mercury.sqkon.db.paging.OffsetQueryPagingSource
import com.mercury.sqkon.db.serialization.KotlinSqkonSerializer
import com.mercury.sqkon.db.serialization.SqkonJson
import com.mercury.sqkon.db.serialization.SqkonSerializer
import com.mercury.sqkon.db.utils.RequestHash
import com.mercury.sqkon.db.utils.nowMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
    protected val metadataQueries: MetadataQueries,
    protected val scope: CoroutineScope,
    protected val type: KType,
    protected val serializer: SqkonSerializer = KotlinSqkonSerializer(),
    protected val config: Config = Config(),
    protected val readDispatcher: CoroutineDispatcher,
    protected val writeDispatcher: CoroutineDispatcher,
) : SuspendingTransacter {

    /**
     * Insert a row.
     *
     * @param ignoreIfExists if true, will not insert if a row with the same key already exists.
     *  Otherwise, throw primary key constraint violation. Useful for "upserting".
     *
     *  @see update
     *  @see upsert
     */
    suspend fun insert(
        key: String, value: T,
        ignoreIfExists: Boolean = false,
        expiresAt: Instant? = null,
    ) = writeContext {
        transaction {
            val now = nowMillis()
            val entity = Entity(
                entity_name = entityName,
                entity_key = key,
                added_at = now,
                updated_at = now,
                expires_at = expiresAt?.toEpochMilliseconds(),
                read_at = null,
                write_at = now,
                value_ = serializer.serialize(type, value) ?: error("Failed to serialize value")
            )
            entityQueries.insertEntity(entity, ignoreIfExists)
            updateWriteAt(currentCoroutineContext()[RequestHash.Key]?.hash ?: entity.hashCode())
        }
    }

    /**
     * Insert multiple rows.
     *
     * @param ignoreIfExists if true, will not insert if a row with the same key already exists.
     * @param expiresAt if set, will be used to expire the row when requesting data before it has
     *  expired.
     *
     * @see updateAll
     * @see upsertAll
     */
    suspend fun insertAll(
        values: Map<String, T>,
        ignoreIfExists: Boolean = false,
        expiresAt: Instant? = null,
    ) = writeContext {
        withContext(RequestHash(values.hashCode())) {
            transaction {
                values.forEach { (key, value) -> insert(key, value, ignoreIfExists, expiresAt) }
            }
        }
    }

    /**
     * Update a row. If the row does not exist, it will update nothing, use [insert] if you want to
     * insert if the row does not exist.
     *
     * We also provide [upsert] convenience function to insert or update.
     *
     * @param expiresAt if set, will be used to expire the row when requesting data before it has
     *   expired.
     * @see insert
     * @see upsert
     */
    suspend fun update(key: String, value: T, expiresAt: Instant? = null) = writeContext {
        transaction {
            entityQueries.updateEntity(
                entityName = entityName,
                entityKey = key,
                expiresAt = expiresAt,
                value = serializer.serialize(type, value) ?: error("Failed to serialize value")
            )
            updateWriteAt(currentCoroutineContext()[RequestHash.Key]?.hash ?: key.hashCode())
        }
    }

    /**
     * Convenience function to insert collection of rows. If the row does not exist, ti will update
     * nothing, use [insert] if you want to insert if the row does not exist.
     *
     * @param expiresAt if set, will be used to expire the row when requesting data before it has
     *  expired.
     * @see insertAll
     * @see upsertAll
     */
    suspend fun updateAll(
        values: Map<String, T>, expiresAt: Instant? = null
    ) = writeContext {
        withContext(RequestHash(values.hashCode())) {
            transaction { values.forEach { (key, value) -> update(key, value, expiresAt) } }
        }
    }


    /**
     * Convenience function to insert a new row or update an existing row.
     *
     * @param expiresAt if set, will be used to expire the row when requesting data before it has
     *   expired.
     * @see insert
     * @see update
     */
    suspend fun upsert(
        key: String, value: T, expiresAt: Instant? = null
    ) = writeContext {
        withContext(RequestHash(key.hashCode())) {
            transaction {
                update(key, value, expiresAt = expiresAt)
                insert(key, value, ignoreIfExists = true, expiresAt = expiresAt)
            }
        }
    }

    /**
     * Convenience function to insert new or update existing multiple rows.
     *
     * Basically an alias for [updateAll] and [insertAll] with ignoreIfExists set to true.
     *
     * @param expiresAt if set, will be used to expire the row when requesting data before it has
     *   expired.
     * @see insertAll
     * @see updateAll
     */
    suspend fun upsertAll(
        values: Map<String, T>,
        expiresAt: Instant? = null
    ) = writeContext {
        withContext(RequestHash(values.hashCode())) {
            transaction {
                values.forEach { (key, value) ->
                    update(key, value, expiresAt = expiresAt)
                    insert(key, value, ignoreIfExists = true, expiresAt = expiresAt)
                }
            }
        }
    }

    /**
     * Select all rows. Effectively an alias for [select] with no where set.
     */
    fun selectAll(
        orderBy: List<OrderBy<T>> = emptyList(),
        expiresAfter: Instant? = null,
    ): Flow<List<T>> {
        return select(where = null, orderBy = orderBy, expiresAfter = expiresAfter)
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
        expiresAfter: Instant? = null,
    ): Flow<List<T>> {
        return entityQueries
            .select(
                entityName = entityName,
                entityKeys = keys,
                orderBy = orderBy,
                expiresAt = expiresAfter,
            )
            .asFlow()
            .mapToList(readDispatcher)
            .onEach { list ->
                updateReadAt(list.map { it.entity_key })
            }
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
        expiresAfter: Instant? = null,
    ): Flow<List<T>> {
        return entityQueries
            .select(
                entityName,
                where = where,
                orderBy = orderBy,
                limit = limit,
                offset = offset,
                expiresAt = expiresAfter,
            )
            .asFlow()
            .mapToList(readDispatcher)
            .onEach { list -> updateReadAt(list.map { it.entity_key }) }
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
     *
     * The result row is useful if you need metadata on the row level specific to Sqkon intead of
     * your entity.
     */
    fun selectResult(
        where: Where<T>? = null,
        orderBy: List<OrderBy<T>> = emptyList(),
        limit: Long? = null,
        offset: Long? = null,
        expiresAfter: Instant? = null,
    ): Flow<List<ResultRow<T>>> {
        return entityQueries
            .select(
                entityName,
                where = where,
                orderBy = orderBy,
                limit = limit,
                offset = offset,
                expiresAt = expiresAfter,
            )
            .asFlow()
            .mapToList(readDispatcher)
            .onEach { list -> updateReadAt(list.map { it.entity_key }) }
            .map { list ->
                if (list.isEmpty()) return@map emptyList<ResultRow<T>>()
                list.mapNotNull { entity ->
                    entity.deserialize<T>()?.let { v -> ResultRow(entity, v) }
                }
            }
            .distinctUntilChanged()
    }

    /**
     * Create a [PagingSource] that pages through results according to queries generated by from the
     * passed in [where] and [orderBy]. [initialOffset] initial offset to start paging from.
     *
     * Queries will be executed on [Config.dispatcher].
     *
     * Note: Offset Paging is not very efficient on large datasets. Use wisely. We are working
     * on supporting [keyset paging](https://sqldelight.github.io/sqldelight/2.0.2/common/androidx_paging_multiplatform/#keyset-paging) in the future.
     *
     * @param expiresAfter null ignores expiresAt, will not return any row which has expired set
     *   and is before expiresAfter. This is normally [Clock.System.now].
     */
    fun selectPagingSource(
        where: Where<T>? = null,
        orderBy: List<OrderBy<T>> = emptyList(),
        initialOffset: Int = 0,
        expiresAfter: Instant? = null,
    ): PagingSource<Int, T> = OffsetQueryPagingSource(
        queryProvider = { limit, offset ->
            entityQueries.select(
                entityName,
                where = where,
                orderBy = orderBy,
                limit = limit.toLong(),
                offset = offset.toLong(),
                expiresAt = expiresAfter,
            ).also { entities ->
                updateReadAt(entities.executeAsList().map { it.entity_key })
            }
        },
        countQuery = entityQueries.count(entityName, where = where),
        transacter = entityQueries,
        context = readDispatcher,
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
        deleteByKeys(key)
    }

    /**
     * Delete by keys.
     *
     * If you need to delete all rows, use [deleteAll].
     * If you need to specify which rows to delete, use [delete] with a [Where]. Note, using where
     * will be less performant than deleting by key.
     *
     * @see delete
     * @see deleteAll
     */
    suspend fun deleteByKeys(vararg key: String) = writeContext {
        transaction {
            entityQueries.delete(entityName, entityKeys = key.toSet())
            updateWriteAt(currentCoroutineContext()[RequestHash.Key]?.hash ?: key.hashCode())
        }
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
    suspend fun delete(where: Where<T>? = null) = writeContext {
        transaction {
            entityQueries.delete(entityName, where = where)
            updateWriteAt(currentCoroutineContext()[RequestHash.Key]?.hash ?: where.hashCode())
        }
    }

    /**
     * Purge all rows that have there `expired_at` field NOT null and less than (<) the date passed
     * in. (Usually [Clock.System.now]).
     *
     * For example to have a 24 hour expiry you would insert with `expiresAt = Clock.System.now().plus(1.days)`.
     * When querying you pass in select(expiresAfter = Clock.System.now()) to only get rows that have not expired.
     * If you want to then clean-up/purge those expired rows, you would call this function.
     *
     * @see deleteStale
     */
    suspend fun deleteExpired(expiresAfter: Instant = Clock.System.now()) = writeContext {
        transaction {
            metadataQueries.purgeExpires(entityName, expiresAfter.toEpochMilliseconds())
            updateWriteAt(
                currentCoroutineContext()[RequestHash.Key]?.hash ?: expiresAfter.hashCode()
            )
        }
    }

    /**
     * Unlike [deleteExpired], this will clean up rows that have not been touched (read/written)
     * before the passed in time.
     *
     * For example, you want to clean up rows that have not been read or written to in the last 24
     * hours. You would call this function with `Clock.System.now().minus(1.days)`. This is not the same as
     * [deleteExpired] which is based on the `expires_at` field.
     *
     * @param writeInstant if set, will delete rows that have not been written to before this time.
     * @param readInstant if set, will delete rows that have not been read before this time.
     *
     * @see deleteExpired
     */
    suspend fun deleteStale(
        writeInstant: Instant? = Clock.System.now(),
        readInstant: Instant? = Clock.System.now()
    ) = writeContext {
        transaction {
            when {
                writeInstant != null && readInstant != null -> {
                    metadataQueries.purgeStale(
                        entity_name = entityName,
                        writeInstant = writeInstant.toEpochMilliseconds(),
                        readInstant = readInstant.toEpochMilliseconds()
                    )
                }

                writeInstant != null -> {
                    metadataQueries.purgeStaleWrite(
                        entity_name = entityName,
                        writeInstant = writeInstant.toEpochMilliseconds()
                    )
                }

                readInstant != null -> {
                    metadataQueries.purgeStaleRead(
                        entity_name = entityName,
                        readInstant = readInstant.toEpochMilliseconds()
                    )
                }

                else -> return@transaction
            }
            updateWriteAt(
                currentCoroutineContext()[RequestHash.Key]?.hash
                    ?: (writeInstant.hashCode() + readInstant.hashCode())
            )
        }
    }

    /**
     * Unlike [deleteExpired], this will clean up rows that have not been touched (read/written)
     * before the passed in time.
     *
     * For example, you want to clean up rows that have not been read or written to in the last 24
     * hours. You would call this function with `Clock.System.now().minus(1.days)`. This is not the same as
     * [deleteExpired] which is based on the `expires_at` field.
     *
     * @see deleteExpired
     */
    suspend fun deleteState(instant: Instant) {
        deleteStale(instant, instant)
    }

    fun count(
        where: Where<T>? = null,
        expiresAfter: Instant? = null
    ): Flow<Int> {
        return entityQueries.count(entityName, where, expiresAfter)
            .asFlow()
            .mapToOne(readDispatcher)
    }

    /**
     * Metadata for the entity, this will tell you the last time
     * the entity store was read and written to, useful for cache invalidation.
     */
    fun metadata(): Flow<Metadata> = metadataQueries
        .selectByEntityName(entityName)
        .asFlow()
        .mapToOneNotNull(readDispatcher)
        .distinctUntilChanged()

    private fun <T : Any> Entity?.deserialize(): T? {
        this ?: return null
        return try {
            serializer.deserialize(type, value_) ?: error("Failed to deserialize value")
        } catch (e: Exception) {
            when (config.deserializePolicy) {
                DeserializePolicy.ERROR -> throw e
                DeserializePolicy.DELETE -> {
                    scope.launch(writeDispatcher) { deleteByKey(entity_key) }
                    null
                }
            }
        }
    }

    private fun updateReadAt(keys: Collection<String>) {
        scope.launch(writeDispatcher) {
            metadataQueries.upsertRead(entityName, Clock.System.now())
            metadataQueries.updateReadForEntities(
                Clock.System.now().toEpochMilliseconds(), entityName, keys
            )
        }
    }

    private val updateWriteHashes = mutableSetOf<Int>()

    /**
     * Will run after the transaction is committed. This way inside of multiple inserts we only
     * update the write_at once.
     */
    private fun TransactionCallbacks.updateWriteAt(requestHash: Int) {
        if (requestHash in updateWriteHashes) return
        updateWriteHashes.add(requestHash)
        afterCommit {
            updateWriteHashes.remove(requestHash)
            scope.launch { metadataQueries.upsertWrite(entityName, Clock.System.now()) }
        }
    }

    /**
     * Will check if already on the same dispatcher/writer and if not, will switch to the
     * dispatcher/writer.
     */
    private suspend fun <T> writeContext(block: suspend CoroutineScope.() -> T): T {
        return withContext(writeDispatcher) { block() }
    }

    // We force the transaction on to our writeContext to make sure we nest the enclosing
    // transactions, otherwise we can create locks by transactions being started on different
    // dispatchers.
    override suspend fun transaction(
        noEnclosing: Boolean,
        body: suspend SuspendingTransactionWithoutReturn.() -> Unit
    ) = writeContext {
        entityQueries.transaction(noEnclosing, body)
    }

    // We force the transaction on to our writeContext to make sure we nest the enclosing
    // transactions, otherwise we can create locks by transactions being started on different
    // dispatchers.
    override suspend fun <R> transactionWithResult(
        noEnclosing: Boolean,
        bodyWithReturn: suspend SuspendingTransactionWithReturn<R>.() -> R
    ): R = writeContext {
        entityQueries.transactionWithResult(noEnclosing, bodyWithReturn)
    }

    data class Config(
        val deserializePolicy: DeserializePolicy = DeserializePolicy.ERROR,
        @Deprecated("Use we use predefined dispatchers for read/write. This is unused now.")
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
    metadataQueries: MetadataQueries,
    scope: CoroutineScope,
    serializer: SqkonSerializer = KotlinSqkonSerializer(),
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
    readDispatcher: CoroutineDispatcher = dbReadDispatcher,
    writeDispatcher: CoroutineDispatcher = dbWriteDispatcher,
): KeyValueStorage<T> {
    return KeyValueStorage(
        entityName = entityName,
        entityQueries = entityQueries,
        metadataQueries = metadataQueries,
        scope = scope,
        type = typeOf<T>(),
        serializer = serializer,
        config = config,
        readDispatcher = readDispatcher,
        writeDispatcher = writeDispatcher,
    )
}
