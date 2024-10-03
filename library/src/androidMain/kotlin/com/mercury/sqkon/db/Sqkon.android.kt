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
    json: Json = SqkonJson { }
): Sqkon {
    val factory = DriverFactory(context)
    val entities = createEntityQueries(factory)
    return Sqkon(entities, scope, json)
}
