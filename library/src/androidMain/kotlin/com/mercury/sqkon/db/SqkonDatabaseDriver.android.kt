package com.mercury.sqkon.db

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.File
import com.eygraber.sqldelight.androidx.driver.SqliteJournalMode
import com.eygraber.sqldelight.androidx.driver.SqliteSync
import kotlinx.coroutines.Dispatchers

internal val connectionPoolSize by lazy { getWALConnectionPoolSize() }
internal val dbWriteDispatcher by lazy { Dispatchers.IO.limitedParallelism(1) }
internal val dbReadDispatcher by lazy { Dispatchers.IO.limitedParallelism(connectionPoolSize) }

/**
 * @param name The name of the database to open or create. If null, an in-memory database will
 *  be created, which will not persist across application restarts. Defaults to "sqkon.db".
 */
internal actual class DriverFactory(
    private val context: Context,
    private val name: String? = "sqkon.db"
) {
    actual fun createDriver(): SqlDriver {
        return AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = when (name) {
                null -> AndroidxSqliteDatabaseType.Memory
                else -> AndroidxSqliteDatabaseType.File(context = context, name = name)
            },
            schema = SqkonDatabase.Schema.synchronous(),
            configuration = AndroidxSqliteConfiguration(
                journalMode = SqliteJournalMode.WAL,
                sync = SqliteSync.Normal,
                readerConnectionsCount = connectionPoolSize,
            )
        )
    }
}


@SuppressLint("DiscouragedApi")
private fun getWALConnectionPoolSize(): Int {
    val resources = Resources.getSystem()
    val resId = resources.getIdentifier("db_connection_pool_size", "integer", "android")
    return if (resId != 0) {
        resources.getInteger(resId)
    } else {
        4 // Default is 4 readers as per AndroidxSqliteConfiguration
    }
}