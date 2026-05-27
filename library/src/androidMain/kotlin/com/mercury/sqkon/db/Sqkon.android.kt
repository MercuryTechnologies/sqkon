package com.mercury.sqkon.db

import android.content.Context
import com.mercury.sqkon.db.serialization.SqkonJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

@Deprecated("Use the dbFileName overload instead")
fun Sqkon(
    context: Context,
    scope: CoroutineScope,
    json: Json = SqkonJson { },
    inMemory: Boolean = false,
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
): Sqkon = Sqkon(
    context = context,
    scope = scope,
    json = json,
    dbFileName = if (inMemory) null else "sqkon.db",
    config = config,
)

/**
 * Main entry point for Sqkon on Android.
 *
 * @param dbFileName name of the db file on disk; null = in-memory.
 */
@JvmOverloads
fun Sqkon(
    context: Context,
    scope: CoroutineScope,
    json: Json = SqkonJson { },
    dbFileName: String? = "sqkon.db",
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
    driverConfig: SqkonDriverConfig = SqkonDriverConfig(),
    dispatchers: SqkonDispatchers = defaultSqkonDispatchers,
): Sqkon {
    val type: SqkonDatabaseType = when (dbFileName) {
        null -> SqkonDatabaseType.Memory
        else -> SqkonDatabaseType.FileBacked(context.getDatabasePath(dbFileName).absolutePath)
    }
    val factory = DriverFactory(context = context, type = type, config = driverConfig)
    val driver = factory.createDriver()
    val metadataQueries = MetadataQueries(driver)
    val entityQueries = EntityQueries(driver)
    return Sqkon(
        entityQueries, metadataQueries, scope, json, config,
        dispatchers = dispatchers,
    )
}
