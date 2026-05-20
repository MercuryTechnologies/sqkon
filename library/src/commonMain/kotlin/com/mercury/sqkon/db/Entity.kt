package com.mercury.sqkon.db

/**
 * Row in the `entity` table. Field names match the SQL columns verbatim, including
 * the trailing underscore on [value_] — SQLDelight escapes the reserved word `value`,
 * and the rest of the codebase references it as `value_`.
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
