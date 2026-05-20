package com.mercury.sqkon.db

/**
 * Row in the `entity` table. The trailing `_` on [value_] dodges Kotlin's `value`
 * contextual keyword; the rest of the codebase references the property by that name.
 */
data class Entity(
    val entity_name: String,
    val entity_key: String,
    val added_at: Long,
    val updated_at: Long,
    val expires_at: Long?,
    val value_: String,
    val read_at: Long?,
    val write_at: Long?,
)
