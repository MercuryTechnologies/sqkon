package com.mercury.sqkon.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.SqliteJournalMode
import com.eygraber.sqldelight.androidx.driver.SqliteSync
import kotlinx.coroutines.Dispatchers

internal const val connectionPoolSize = 4 // Default is 4 as per AndroidxSqliteConfiguration
internal val dbWriteDispatcher by lazy { Dispatchers.IO.limitedParallelism(1) }
internal val dbReadDispatcher by lazy { Dispatchers.IO.limitedParallelism(connectionPoolSize) }

internal actual class DriverFactory(
    private val databaseType: AndroidxSqliteDatabaseType = AndroidxSqliteDatabaseType.Memory,
) {

    actual fun createDriver(): SqlDriver {
        return AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = databaseType,
            schema = SqkonDatabase.Schema.synchronous(),
            configuration = AndroidxSqliteConfiguration(
                journalMode = SqliteJournalMode.WAL,
                sync = SqliteSync.Normal,
                readerConnectionsCount = connectionPoolSize,
            )
        )
    }

}
