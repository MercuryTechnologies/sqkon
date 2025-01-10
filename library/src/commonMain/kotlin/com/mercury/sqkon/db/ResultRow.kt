package com.mercury.sqkon.db

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
        readAt = entity.read_at?.let { Instant.fromEpochMilliseconds(it) },
        writeAt = Instant.fromEpochMilliseconds(entity.write_at),
        value = value,
    )
}
