package com.mercury.sqkon.db

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class ResultRow<T : Any>(
    val addedAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val readAt: Instant?,
    val writeAt: Instant,
    val value: T,
) {
    internal constructor(entity: Entity, value: T) : this(
        addedAt = Instant.fromEpochMilliseconds(entity.added_at),
        updatedAt = Instant.fromEpochMilliseconds(entity.updated_at),
        expiresAt = entity.expires_at?.let { Instant.fromEpochMilliseconds(it) },
        // By reading this value, we are marking it as read, we just update the db async
        readAt = Clock.System.now(),
        // While write_at col is nullable, we always set it. (Sqlite limitation)
        writeAt = Instant.fromEpochMilliseconds(entity.write_at!!),
        value = value,
    )
}
