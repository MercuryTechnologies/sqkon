package com.mercury.sqkon.db.adapters

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.Instant

internal class InstantColumnAdapter : ColumnAdapter<Instant, Long> {
    override fun decode(databaseValue: Long): Instant {
        return Instant.fromEpochMilliseconds(databaseValue)
    }

    override fun encode(value: Instant): Long {
        return value.toEpochMilliseconds()
    }
}