package com.mercury.sqkon.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver

internal actual class DriverFactory(
    private val databaseType: AndroidxSqliteDatabaseType = AndroidxSqliteDatabaseType.Memory,
) {

    actual fun createDriver(): SqlDriver {
        return AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = databaseType,
            schema = SqkonDatabase.Schema.synchronous(),
        )
    }

}
