package com.mercury.sqkon.db

import com.mercury.sqkon.db.serialization.SqkonJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

@JvmOverloads
fun Sqkon(
    scope: CoroutineScope,
    json: Json = SqkonJson { },
    type: SqkonDatabaseType = SqkonDatabaseType.Memory,
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
    driverConfig: SqkonDriverConfig = SqkonDriverConfig(),
    dispatchers: SqkonDispatchers = defaultSqkonDispatchers,
): Sqkon {
    val factory = DriverFactory(type, driverConfig)
    val driver = factory.createDriver()
    val metadataQueries = MetadataQueries(driver)
    val entityQueries = EntityQueries(driver)
    return Sqkon(
        entityQueries, metadataQueries, scope, json, config,
        dispatchers = dispatchers,
    )
}
