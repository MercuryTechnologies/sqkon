package com.mercury.sqkon.db.internal

interface SqkonStatement {
    fun bindBytes(index: Int, value: ByteArray?)
    fun bindBoolean(index: Int, value: Boolean?)
    fun bindDouble(index: Int, value: Double?)
    fun bindLong(index: Int, value: Long?)
    fun bindString(index: Int, value: String?)
}
