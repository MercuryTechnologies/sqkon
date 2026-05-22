package com.mercury.sqkon.db.internal.schema

import com.mercury.sqkon.db.internal.SqkonDriver

internal const val SQKON_SCHEMA_VERSION: Long = 2L

internal interface SqkonSchema {
    val version: Long

    fun create(driver: SqkonDriver)

    fun migrate(driver: SqkonDriver, oldVersion: Long, newVersion: Long)
}
