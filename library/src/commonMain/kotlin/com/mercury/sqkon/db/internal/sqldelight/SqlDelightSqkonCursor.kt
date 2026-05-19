package com.mercury.sqkon.db.internal.sqldelight

import app.cash.sqldelight.db.SqlCursor
import com.mercury.sqkon.db.internal.SqkonCursor

internal class SqlDelightSqkonCursor(
    private val delegate: SqlCursor,
) : SqkonCursor {
    override fun next(): Boolean = delegate.next().value
    override fun getString(index: Int): String? = delegate.getString(index)
    override fun getLong(index: Int): Long? = delegate.getLong(index)
    override fun getBytes(index: Int): ByteArray? = delegate.getBytes(index)
    override fun getDouble(index: Int): Double? = delegate.getDouble(index)
    override fun getBoolean(index: Int): Boolean? = delegate.getBoolean(index)
}
