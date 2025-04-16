package com.mercury.sqkon.db

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.mercury.sqkon.db.serialization.SqkonJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

fun Sqkon(
    scope: CoroutineScope,
    json: Json = SqkonJson { },
    type: AndroidxSqliteDatabaseType = AndroidxSqliteDatabaseType.Memory,
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
): Sqkon {
    val factory = DriverFactory(type)
    val driver = factory.createDriver()
    val metadataQueries = MetadataQueries(driver)
    val entityQueries = EntityQueries(driver)
    return Sqkon(
        entityQueries, metadataQueries, scope, json, config,
        readDispatcher = dbReadDispatcher,
        writeDispatcher = dbWriteDispatcher,
    )
}
