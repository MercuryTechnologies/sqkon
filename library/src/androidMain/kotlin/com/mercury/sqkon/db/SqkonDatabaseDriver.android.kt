package com.mercury.sqkon.db

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.File
import com.eygraber.sqldelight.androidx.driver.SqliteJournalMode
import com.eygraber.sqldelight.androidx.driver.SqliteSync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newFixedThreadPoolContext

internal actual val connectionPoolSize: Int by lazy { getWALConnectionPoolSize() }

@OptIn(DelicateCoroutinesApi::class)
@PublishedApi
internal actual val dbWriteDispatcher: CoroutineDispatcher by lazy {
    newFixedThreadPoolContext(nThreads = 1, "SqkonReadDispatcher")
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
    private val driver = BundledSQLiteDriver()
    actual fun createDriver(): SqlDriver {
        return AndroidxSqliteDriver(
            createConnection = {
                driver.open(
                    fileName = it,
                    flags = SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX
                )
            },
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