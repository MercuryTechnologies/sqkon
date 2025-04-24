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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val connectionPoolSize: Int by lazy { getWALConnectionPoolSize() }

@PublishedApi
internal actual val dbWriteDispatcher: CoroutineDispatcher by lazy {
    Dispatchers.IO.limitedParallelism(1)
}

@PublishedApi
internal actual val dbReadDispatcher: CoroutineDispatcher by lazy {
    Dispatchers.IO.limitedParallelism(connectionPoolSize)
}

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