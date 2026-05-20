package com.mercury.sqkon.db

import kotlinx.datetime.Instant

/**
 * Row in the `metadata` table. [MetadataQueries] handles the `Long ↔ Instant`
 * conversion in its cursor mapper and upsert bind paths.
 */
data class Metadata(
    val entity_name: String,
    val lastReadAt: Instant?,
    val lastWriteAt: Instant?,
)
