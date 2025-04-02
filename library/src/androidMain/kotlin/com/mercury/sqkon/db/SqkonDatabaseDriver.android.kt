package com.mercury.sqkon.db

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.File

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
            schema = SqkonDatabase.Schema.synchronous()
        )
    }
}