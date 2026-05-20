package com.mercury.sqkon.db

import kotlinx.datetime.Instant

/**
 * Row in the `metadata` table. The `Long ↔ Instant` conversion that used to live in
 * `Metadata.Adapter` is now folded into the cursor mappers and the upsert bind paths
 * in [MetadataQueries].
 */
data class Metadata(
    val entity_name: String,
    val lastReadAt: Instant?,
    val lastWriteAt: Instant?,
)
