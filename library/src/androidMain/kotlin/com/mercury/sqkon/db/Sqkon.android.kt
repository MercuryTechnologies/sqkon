package com.mercury.sqkon.db

import android.content.Context
import com.mercury.sqkon.db.serialization.SqkonJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

/**
 * Main entry point for Sqkon on Android
 */
fun Sqkon(
    context: Context,
    scope: CoroutineScope,
    json: Json = SqkonJson { },
    inMemory: Boolean = false,
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
): Sqkon {
    val factory = DriverFactory(context, if (inMemory) null else "sqkon.db")
    val entities = createEntityQueries(factory)
    return Sqkon(entities, scope, json, config)
}
