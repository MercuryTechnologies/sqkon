package com.mercury.sqkon.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConcurrencyModel
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.SqliteJournalMode
import com.eygraber.sqldelight.androidx.driver.SqliteSync
import com.mercury.sqkon.db.internal.schema.SqkonDatabaseSchema
import com.mercury.sqkon.db.internal.sqldelight.toSqlDelightSchema
import kotlinx.coroutines.Dispatchers

internal actual const val connectionPoolSize = 4 // Default is 4 as per AndroidxSqliteConfiguration

@PublishedApi
internal actual val defaultSqkonDispatchers: SqkonDispatchers by lazy {
    SqkonDispatchers(
        read = Dispatchers.IO.limitedParallelism(connectionPoolSize),
        write = Dispatchers.IO.limitedParallelism(1),
    )
}

internal actual class DriverFactory(
    private val databaseType: AndroidxSqliteDatabaseType = AndroidxSqliteDatabaseType.Memory,
) {

    actual fun createDriver(): SqlDriver {
        return AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = databaseType,
            schema = SqkonDatabaseSchema.toSqlDelightSchema(),
            configuration = AndroidxSqliteConfiguration(
                journalMode = SqliteJournalMode.WAL,
                sync = SqliteSync.Normal,
                concurrencyModel = AndroidxSqliteConcurrencyModel.MultipleReadersSingleWriter(
                    isWal = true,
                    walCount = connectionPoolSize,
                ),
            )
        )
    }

}
