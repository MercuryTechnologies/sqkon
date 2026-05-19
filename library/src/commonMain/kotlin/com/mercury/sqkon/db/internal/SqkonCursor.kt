package com.mercury.sqkon.db.internal

interface SqkonCursor {
    fun next(): Boolean
    fun getString(index: Int): String?
    fun getLong(index: Int): Long?
    fun getBytes(index: Int): ByteArray?
    fun getDouble(index: Int): Double?
    fun getBoolean(index: Int): Boolean?
}
