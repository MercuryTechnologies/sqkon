package com.mercury.sqkon.db

import com.mercury.sqkon.db.serialization.KotlinSqkonSerializer
import com.mercury.sqkon.db.serialization.SqkonJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

/**
 * Main entry point for Sqkon.
 *
 * Each platform has a `Sqkon` function that creates a `Sqkon` instance.
 *
 * Simple usage:
 * ```
 * val sqkon = Sqkon()
 * val merchantStore = sqkon.keyValueStorage<Merchant>("merchant")
 * merchantStore.insert(
 *  id = "123",
 *  value = Merchant(id = "123", name = "Chipotle", category = "Restaurant")
 * )
 * val merchant = merchantStore.selectByKey("123").first()
 * val merchants = merchantStore.select(where = Merchant::category like "Restaurant")
 * ```
 */
class Sqkon internal constructor(
    @PublishedApi internal val entityQueries: EntityQueries,
    @PublishedApi internal val metadataQueries: MetadataQueries,
    @PublishedApi internal val scope: CoroutineScope,
    json: Json = SqkonJson {},
    @PublishedApi
    internal val config: KeyValueStorage.Config = KeyValueStorage.Config(),
) {

    @PublishedApi
    internal val serializer = KotlinSqkonSerializer(json)


    /**
     * Create a KeyValueStorage for the given entity name.
     *
     * @param T the type of the entity to store.
     * @param name the name of the entity to store.
     * @param config configuration for the KeyValueStorage. Overrides the default configuration
     *  passed into Sqkon.
     */
    inline fun <reified T : Any> keyValueStorage(
        name: String,
        config: KeyValueStorage.Config = this.config,
    ): KeyValueStorage<T> {
        return keyValueStorage<T>(name, entityQueries, metadataQueries, scope, serializer, config)
    }

}
