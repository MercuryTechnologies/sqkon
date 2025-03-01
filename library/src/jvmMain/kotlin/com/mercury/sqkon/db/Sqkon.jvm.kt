package com.mercury.sqkon.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.mercury.sqkon.db.serialization.SqkonJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

fun Sqkon(
    scope: CoroutineScope,
    json: Json = SqkonJson { },
    jdbcUrl: String = JdbcSqliteDriver.IN_MEMORY,
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
): Sqkon {
    val factory = DriverFactory(jdbcUrl)
    val driver = factory.createDriver()
    val metadataQueries = MetadataQueries(driver)
    val entityQueries = EntityQueries(driver)
    return Sqkon(entityQueries, metadataQueries, scope, json, config)
}
