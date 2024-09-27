package com.mercury.sqkon.db

import com.mercury.sqkon.db.serialization.KotlinSqkonSerializer
import com.mercury.sqkon.db.serialization.SqkonJson
import kotlinx.serialization.json.Json

class Sqkon internal constructor(
    @PublishedApi internal val entityQueries: EntityQueries,
    json: Json = SqkonJson {}
) {

    @PublishedApi
    internal val serializer = KotlinSqkonSerializer(json)

    inline fun <reified T : Any> keyValueStorage(name: String): KeyValueStorage<T> {
        return keyValueStorage<T>(name, entityQueries, serializer)
    }

}
