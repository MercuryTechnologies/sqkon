package com.mercury.sqkon.db

import android.content.Context
import com.mercury.sqkon.db.serialization.SqkonJson
import kotlinx.serialization.json.Json

/**
 * Main entry point for Sqkon on Android
 */
fun Sqkon(
    context: Context,
    json: Json = SqkonJson { }
): Sqkon {
    val factory = DriverFactory(context)
    val entities = createEntityQueries(factory)
    return Sqkon(entities, json)
}
