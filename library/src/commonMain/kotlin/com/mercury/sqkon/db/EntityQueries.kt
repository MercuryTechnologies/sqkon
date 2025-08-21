package com.mercury.sqkon.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.mercury.sqkon.db.utils.nowMillis
import com.mercury.sqkon.db.serialization.KotlinSqkonSerializer
import com.mercury.sqkon.db.serialization.SqkonSerializer
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.annotations.VisibleForTesting

class EntityQueries(
    @PublishedApi
    internal val sqlDriver: SqlDriver,
) : TransacterImpl(sqlDriver) {

    // Used to slow down insert/updates for testing
    @VisibleForTesting
    internal var slowWrite: Boolean = false

    fun insertEntity(entity: Entity, ignoreIfExists: Boolean) {
        val identifier = identifier("insert", ignoreIfExists.toString())
        val orIgnore = if (ignoreIfExists) "OR IGNORE" else ""
        driver.execute(
            identifier = identifier,
            sql = """
            INSERT $orIgnore INTO entity (
                entity_name, entity_key, added_at, updated_at, expires_at, write_at, value
            ) 
            VALUES (?, ?, ?, ?, ?, ?, jsonb(?))
            """.trimIndent(),
            parameters = 7
        ) {
            bindString(0, entity.entity_name)
            bindString(1, entity.entity_key)
            bindLong(2, entity.added_at)
            bindLong(3, entity.updated_at)
            bindLong(4, entity.expires_at)
            // While write_at is nullable on the db col, we always set it here (sqlite limitation)
            bindLong(5, entity.write_at ?: nowMillis())
            bindString(6, entity.value_)
        }
        notifyQueries(identifier) { emit ->
            emit("entity")
            emit("entity_${entity.entity_name}")
        }
        if (slowWrite) {
            Thread.sleep(100)
        }
    }

    fun updateEntity(
        entityName: String,
        entityKey: String,
        expiresAt: Instant?,
        value: String,
    ) {
        val now = Clock.System.now()
        val identifier = identifier("update")
        driver.execute(
            identifier = identifier,
            sql = """
                UPDATE entity SET updated_at = ?, expires_at = ?, write_at = ?, value = jsonb(?)
                WHERE entity_name = ? AND entity_key = ?
            """.trimMargin(), 5
        ) {
            bindLong(0, now.toEpochMilliseconds())
            bindLong(1, expiresAt?.toEpochMilliseconds())
            bindLong(2, now.toEpochMilliseconds())
            bindString(3, value)
            bindString(4, entityName)
            bindString(5, entityKey)
        }
        notifyQueries(identifier) { emit ->
            emit("entity")
            emit("entity_${entityName}")
        }
        if (slowWrite) {
            Thread.sleep(100)
        }
    }

    fun select(
        entityName: String,
        entityKeys: Collection<String>? = null,
        where: Where<*>? = null,
        orderBy: List<OrderBy<*>> = emptyList(),
        limit: Long? = null,
        offset: Long? = null,
        expiresAt: Instant? = null,
        serializer: SqkonSerializer = KotlinSqkonSerializer(),
    ): Query<Entity> = SelectQuery(
        entityName = entityName,
        entityKeys = entityKeys,
        where = where,
        orderBy = orderBy,
        limit = limit,
        offset = offset,
        expiresAt = expiresAt,
        serializer = serializer,
    ) { cursor ->
        Entity(
            entity_name = cursor.getString(0)!!,
            entity_key = cursor.getString(1)!!,
            added_at = cursor.getLong(2)!!,
            updated_at = cursor.getLong(3)!!,
            expires_at = cursor.getLong(4),
            read_at = cursor.getLong(5),
            write_at = cursor.getLong(6),
            value_ = cursor.getString(7)!!,
        )
    }

    private inner class SelectQuery(
        private val entityName: String,
        private val entityKeys: Collection<String>? = null,
        private val where: Where<*>? = null,
        private val orderBy: List<OrderBy<*>>,
        private val limit: Long? = null,
        private val offset: Long? = null,
        private val expiresAt: Instant? = null,
        private val serializer: SqkonSerializer = KotlinSqkonSerializer(),
        mapper: (SqlCursor) -> Entity,
    ) : Query<Entity>(mapper) {

        override fun addListener(listener: Listener) {
            driver.addListener("entity_$entityName", listener = listener)
        }

        override fun removeListener(listener: Listener) {
            driver.removeListener("entity_$entityName", listener = listener)
        }

        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
            val queries = buildList {
                add(
                    SqlQuery(
                        where = "entity_name = ?",
                        parameters = 1,
                        bindArgs = { bindString(entityName) },
                    )
                )
                if (expiresAt != null) add(
                    SqlQuery(
                        where = "expires_at IS NULL OR expires_at >= ?",
                        parameters = 1,
                        bindArgs = { bindLong(expiresAt.toEpochMilliseconds()) },
                    )
                )
                when (entityKeys?.size) {
                    null, 0 -> {}

                    1 -> add(
                        SqlQuery(
                            where = "entity_key = ?",
                            parameters = 1,
                            bindArgs = { bindString(entityKeys.first()) },
                        )
                    )

                    else -> add(
                        SqlQuery(
                            where = "entity_key IN (${entityKeys.joinToString(",") { "?" }})",
                            parameters = entityKeys.size,
                            bindArgs = { entityKeys.forEach { bindString(it) } },
                        )
                    )
                }
                addAll(listOfNotNull(where?.toSqlQuery(increment = 1)))
                addAll(orderBy.toSqlQueries())
            }
            val identifier: Int = identifier(
                "select",
                queries.identifier().toString(),
                limit?.let { "limit" },
                offset?.let { "offset" },
            )
            val sql = """
                SELECT DISTINCT entity.entity_name, entity.entity_key, entity.added_at, 
                entity.updated_at, entity.expires_at, entity.read_at, entity.write_at, 
                json_extract(entity.value, '$') value
                FROM entity${queries.buildFrom()} ${queries.buildWhere()} ${queries.buildOrderBy()}
                ${limit?.let { "LIMIT ?" } ?: ""} ${offset?.let { "OFFSET ?" } ?: ""}
            """.trimIndent().replace('\n', ' ')

            println(sql)

            return try {
                driver.executeQuery(
                    identifier = identifier,
                    sql = sql.replace('\n', ' '),
                    mapper = mapper,
                    parameters = queries.sumParameters() + (if (limit != null) 1 else 0) + (if (offset != null) 1 else 0),
                ) {
                    val binder = AutoIncrementSqlPreparedStatement(preparedStatement = this, serializer = serializer)
                    queries.forEach { it.bindArgs(binder) }
                    if (limit != null) binder.bindLong(limit)
                    if (offset != null) binder.bindLong(offset)
                }
            } catch (ex: SqlException) {
                println("SQL Error: $sql")
                throw ex
            }
        }

        override fun toString(): String = "select"
    }

    fun delete(
        entityName: String,
        entityKeys: Collection<String>? = null,
        where: Where<*>? = null,
        serializer: SqkonSerializer = KotlinSqkonSerializer(),
    ) {
        val queries = buildList {
            add(
                SqlQuery(
                    where = "entity_name = ?",
                    parameters = 1,
                    bindArgs = { bindString(entityName) }
                ))
            when (entityKeys?.size) {
                null, 0 -> {}

                1 -> add(
                    SqlQuery(
                        where = "entity_key = ?",
                        parameters = 1,
                        bindArgs = { bindString(entityKeys.first()) }
                    ))

                else -> add(
                    SqlQuery(
                        where = "entity_key IN (${entityKeys.joinToString(",") { "?" }})",
                        parameters = entityKeys.size,
                        bindArgs = { entityKeys.forEach { bindString(it) } }
                    ))
            }

            addAll(listOfNotNull(where?.toSqlQuery(increment = 1)))
        }
        val identifier = identifier("delete", queries.identifier().toString())
        val whereSubQuerySql = if (queries.size <= 1) ""
        else """
            AND entity_key IN (SELECT entity_key FROM entity${queries.buildFrom()} ${queries.buildWhere()})
            """.trimIndent()
        val sql = "DELETE FROM entity WHERE entity_name = ? $whereSubQuerySql"
        try {
            driver.execute(
                identifier = identifier,
                sql = sql.replace('\n', ' '),
                parameters = 1 + if (queries.size > 1) queries.sumParameters() else 0,
            ) {
                bindString(0, entityName)
                val preparedStatement = AutoIncrementSqlPreparedStatement(1, this, serializer)
                if (queries.size > 1) {
                    queries.forEach { q -> q.bindArgs(preparedStatement) }
                }
            }
        } catch (ex: SqlException) {
            println("SQL Error: $sql")
            throw ex
        }
        notifyQueries(identifier) { emit ->
            emit("entity")
            emit("entity_$entityName")
        }
    }

    fun count(
        entityName: String,
        where: Where<*>? = null,
        expiresAfter: Instant? = null,
        serializer: SqkonSerializer = KotlinSqkonSerializer(),
    ): Query<Int> = CountQuery(entityName, where, expiresAfter, serializer) { cursor ->
        cursor.getLong(0)!!.toInt()
    }

    private inner class CountQuery<out T : Any>(
        private val entityName: String,
        private val where: Where<*>? = null,
        private val expiresAfter: Instant? = null,
        private val serializer: SqkonSerializer = KotlinSqkonSerializer(),
        mapper: (SqlCursor) -> T,
    ) : Query<T>(mapper) {

        override fun addListener(listener: Listener) {
            driver.addListener("entity_$entityName", listener = listener)
        }

        override fun removeListener(listener: Listener) {
            driver.removeListener("entity_$entityName", listener = listener)
        }

        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
            val queries = buildList {
                add(
                    SqlQuery(
                        where = "entity_name = ?",
                        parameters = 1,
                        bindArgs = { bindString(entityName) }
                    ))
                if (expiresAfter != null) add(
                    SqlQuery(
                        where = "expires_at IS NULL OR expires_at >= ?",
                        parameters = 1,
                        bindArgs = { bindLong(expiresAfter.toEpochMilliseconds()) }
                    )
                )
                addAll(listOfNotNull(where?.toSqlQuery(increment = 1)))
            }
            val identifier: Int = identifier("count", queries.identifier().toString())
            val sql = """
                SELECT COUNT(*) FROM entity${queries.buildFrom()} ${queries.buildWhere()}
            """.trimIndent().replace('\n', ' ')
            return try {
                driver.executeQuery(
                    identifier = identifier,
                    sql = sql,
                    mapper = mapper,
                    parameters = queries.sumParameters(),
                ) {
                    val binder = AutoIncrementSqlPreparedStatement(preparedStatement = this, serializer = serializer)
                    queries.forEach { it.bindArgs(binder) }
                }
            } catch (ex: SqlException) {
                println("SQL Error: $sql")
                throw ex
            }
        }

        override fun toString(): String = "count"
    }

}

/**
 * Generate an identifier for a query based on changing query sqlstring
 * (binding parameters don't change the structure of the string)
 */
private fun identifier(vararg values: String?): Int {
    return values.filterNotNull().joinToString("_").hashCode()
}


expect class SqlException : Exception
