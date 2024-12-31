package com.mercury.sqkon.db

import app.cash.sqldelight.db.SqlDriver
import com.mercury.sqkon.db.adapters.InstantColumnAdapter

/**
 * Factory method to create [MetadataQueries] instance
 */
internal fun MetadataQueries(driver: SqlDriver): MetadataQueries {
    return MetadataQueries(
        driver = driver,
        metadataAdapter = Metadata.Adapter(
            lastWriteAtAdapter = InstantColumnAdapter(),
            lastReadAtAdapter = InstantColumnAdapter(),
        )
    )
}