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
        val identifier = identifier("insert", entity.entity_name)
        driver.execute(
            identifier,
            """
            INSERT INTO entity (entity_name, entity_key, added_at, updated_at, expires_at, value) 
            VALUES (?, ?, ?, ?, ?, jsonb('${entity.value_}'))
            """.trimIndent(),
            5
        ) {
            bindString(0, entity.entity_name)
            bindString(1, entity.entity_key)
            bindLong(2, entity.added_at)
            bindLong(3, entity.updated_at)
            bindLong(4, entity.expires_at)
            //bindString(5, entity.value_)
        }.await()
        println("insert entity.value: ${entity.value_}")
        notifyQueries(identifier) { emit ->
            emit("entity")
            emit("entity_${entity.entity_name}")
        }
    }


    fun <T : Any> selectAll(entityName: String, mapper: (value: String) -> T): Query<T> {
        return SelectByNameQuery(entityName) { cursor ->
            Entity(
                cursor.getString(0)!!,
                cursor.getString(1)!!,
                cursor.getLong(2)!!,
                cursor.getLong(3)!!,
                cursor.getLong(4)!!,
                cursor.getString(5)!!,
            ).let { entity ->
                mapper(entity.value_)
            }
        }
    }

    private inner class SelectByNameQuery<out T : Any>(
        private val entityName: String,
        private val order: Pair<String, String>? = null,
        mapper: (SqlCursor) -> T,
    ) : Query<T>(mapper) {

        private val identifier: Int = identifier("selectByName", entityName)

        override fun addListener(listener: Listener) {
            driver.addListener("entity", "entity_$entityName", listener = listener)
        }

        override fun removeListener(listener: Listener) {
            driver.removeListener("entity", "entity_$entityName", listener = listener)
        }

        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
            driver.executeQuery(
                identifier = identifier,
                sql = """
                SELECT entity.entity_name, entity.entity_key, entity.added_at, entity.updated_at, entity.expires_at, 
                json_extract(entity.value, '$') value
                FROM entity WHERE entity_name = ?
                ORDER BY jsonb_extract(entity.value, '$.name')"
                """.trimIndent(),
                mapper,
                parameters = 1
            ) {
                bindString(0, entityName)
            }

        override fun toString(): String = "selectByName"
    }

}

private fun identifier(function: String, entityName: String): Int {
    return "${function}_${entityName}".hashCode()
}