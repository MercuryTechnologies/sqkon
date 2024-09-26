package com.mercury.sqkon.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver

class EntityQueries(
    driver: SqlDriver,
) : SuspendingTransacterImpl(driver) {

    suspend fun insertEntity(entity: Entity) {
        val identifier = identifier("insert")
        driver.execute(
            identifier = identifier,
            sql = """
            INSERT INTO entity (entity_name, entity_key, added_at, updated_at, expires_at, value) 
            VALUES (?, ?, ?, ?, ?, jsonb(?))
            """.trimIndent(),
            parameters = 6
        ) {
            bindString(0, entity.entity_name)
            bindString(1, entity.entity_key)
            bindLong(2, entity.added_at)
            bindLong(3, entity.updated_at)
            bindLong(4, entity.expires_at)
            bindString(5, entity.value_)
        }.await()
        notifyQueries(identifier) { emit ->
            emit("entity")
            emit("entity_${entity.entity_name}")
        }
    }

    suspend fun updateEntity(
        entityName: String,
        entityKey: String,
        updatedAt: Long,
        expiresAt: Long?,
        value: String,
    ) {
        val identifier = identifier("update")
        driver.execute(
            identifier = identifier,
            sql = """
                UPDATE entity SET updated_at = ?, expires_at = ?, value = jsonb(?)
                WHERE entity_name = ? AND entity_key = ?
            """.trimMargin(), 5
        ) {
            bindLong(0, updatedAt)
            bindLong(1, expiresAt)
            bindString(2, value)
            bindString(3, entityName)
            bindString(4, entityKey)
        }.await()
        notifyQueries(identifier) { emit ->
            emit("entity")
            emit("entity_${entityName}")
        }
    }

    fun <T : Any> select(
        entityName: String,
        entityKey: String? = null,
        mapper: (value: String) -> T,
        where: Where<T>? = null,
        orderBy: List<OrderBy<T>> = emptyList(),
    ): Query<T> = SelectQuery(
        entityName = entityName,
        entityKey = entityKey,
        where = where,
        orderBy = orderBy
    ) { cursor ->
        Entity(
            cursor.getString(0)!!,
            cursor.getString(1)!!,
            cursor.getLong(2)!!,
            cursor.getLong(3)!!,
            cursor.getLong(4),
            cursor.getString(5)!!,
        ).let { entity ->
            mapper(entity.value_)
        }
    }

    private inner class SelectQuery<out T : Any>(
        private val entityName: String,
        private val entityKey: String? = null,
        private val where: Where<T>? = null,
        private val orderBy: List<OrderBy<T>>,
        mapper: (SqlCursor) -> T,
    ) : Query<T>(mapper) {

        private val identifier: Int = identifier(
            "select", if (entityKey != null) "key" else null,
            //TODO add where for identifier (needs to use binding parameters)
            orderBy.identifier()
        )

        override fun addListener(listener: Listener) {
            driver.addListener("entity_$entityName", listener = listener)
        }

        override fun removeListener(listener: Listener) {
            driver.removeListener("entity_$entityName", listener = listener)
        }

        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
            val orderBy = orderBy.toSqlString()
            val where = where.toSqlString("tree.fullkey", "tree.value")
                .let { if (it.isNotEmpty()) " AND ($it)" else "" }
            val whereEntityKey = if (entityKey != null) " AND entity_key = ?" else ""
            val sql = """
                SELECT DISTINCT entity.entity_name, entity.entity_key, entity.added_at, entity.updated_at, entity.expires_at, 
                json_extract(entity.value, '$') value
                FROM entity, json_tree(entity.value, '$') as tree
                WHERE entity.entity_name = ?$whereEntityKey$where
                $orderBy
            """.trimIndent()
            println("Sql $sql")
            return try {
                driver.executeQuery(
                    identifier = identifier,
                    sql = sql.replace('\n', ' '),
                    mapper = mapper,
                    parameters = (1 + if (entityKey != null) 1 else 0)
                ) {
                    bindString(0, entityName)
                    if (entityKey != null) bindString(1, entityKey)
                }
            } catch (ex: SqlException) {
                println("SQL Error: $sql")
                throw ex
            }
        }

        override fun toString(): String = "select"
    }

    suspend fun delete(
        entityName: String,
        entityKey: String? = null,
        where: Where<*>? = null,
    ) {
        //TODO add where for identifier (needs to use binding parameters)
        val identifier = identifier("delete", entityKey, where.toString())
        val whereSubQuerySql =
            where.toSqlString(keyColumn = "tree.fullkey", valueColumn = "tree.value").let {
                if (it.isBlank()) return@let ""
                """
                | AND entity_key = (SELECT entity_key FROM entity, json_tree(entity.value, '$') as tree
                | WHERE entity.entity_name = ? AND $it)
                """.trimMargin().replace("\n", " ")
            }
        val whereEntityKey = if (entityKey != null) " AND entity_key = ?" else ""
        val sql = """
            DELETE FROM entity WHERE entity_name = ?$whereEntityKey$whereSubQuerySql
        """.trimIndent()
        try {
            val paramCount =
                (1 + (if (entityKey != null) 1 else 0) + (if (whereSubQuerySql.isNotBlank()) 1 else 0))
            driver.execute(
                identifier = identifier,
                sql = sql.replace('\n', ' '),
                parameters = paramCount,
            ) {
                var index = 0
                bindString(index, entityName); index++
                if (entityKey != null) {
                    bindString(index, entityKey); index++
                }
                if (whereSubQuerySql.isNotBlank()) {
                    bindString(index, entityName); index++
                }
            }.await()
        } catch (ex: SqlException) {
            println("SQL Error: $sql")
            throw ex
        }
        notifyQueries(identifier) { emit ->
            emit("entity")
            emit("entity_$entityName")
        }
    }

    fun count(entityName: String): Query<Long> = CountQuery(entityName) { cursor ->
        cursor.getLong(0)!!
    }

    private inner class CountQuery<out T : Any>(
        private val entityName: String,
        mapper: (SqlCursor) -> T,
    ) : Query<T>(mapper) {

        private val identifier: Int = identifier("count")

        override fun addListener(listener: Query.Listener) {
            driver.addListener("entity_$entityName", listener = listener)
        }

        override fun removeListener(listener: Query.Listener) {
            driver.removeListener("entity_$entityName", listener = listener)
        }

        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
            driver.executeQuery(
                identifier = identifier,
                sql = """SELECT COUNT(*) FROM entity WHERE entity_name = ?""",
                mapper = mapper,
                parameters = 1
            ) {
                bindString(0, entityName)
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