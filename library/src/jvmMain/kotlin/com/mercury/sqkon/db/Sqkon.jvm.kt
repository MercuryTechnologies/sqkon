package com.mercury.sqkon.db

import com.mercury.sqkon.db.serialization.SqkonJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

fun Sqkon(
    scope: CoroutineScope,
    json: Json = SqkonJson { }
): Sqkon {
    val factory = DriverFactory()
    val entities = createEntityQueries(factory)
    return Sqkon(entities, scope, json)
}
