package com.mercury.sqkon.db.internal.sqldelight

import app.cash.sqldelight.db.SqlPreparedStatement
import com.mercury.sqkon.db.internal.SqkonStatement

internal class SqlDelightSqkonStatement(
    private val delegate: SqlPreparedStatement,
) : SqkonStatement {
    override fun bindBytes(index: Int, value: ByteArray?) = delegate.bindBytes(index, value)
    override fun bindBoolean(index: Int, value: Boolean?) = delegate.bindBoolean(index, value)
    override fun bindDouble(index: Int, value: Double?) = delegate.bindDouble(index, value)
    override fun bindLong(index: Int, value: Long?) = delegate.bindLong(index, value)
    override fun bindString(index: Int, value: String?) = delegate.bindString(index, value)
}
