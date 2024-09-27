package com.mercury.sqkon.db

import com.mercury.sqkon.db.serialization.SqkonJson
import kotlinx.serialization.json.Json

fun Sqkon(
    json: Json = SqkonJson { }
): Sqkon {
    val factory = DriverFactory()
    val entities = createEntityQueries(factory)
    return Sqkon(entities, json)
}