package com.mercury.sqkon.db.internal.androidx

import androidx.sqlite.SQLiteConnection

/** Opens a [SQLiteConnection] by name. Platform actuals supply the open flags (incl. FULLMUTEX). */
internal fun interface SqkonConnectionFactory {
    fun open(name: String): SQLiteConnection
}
