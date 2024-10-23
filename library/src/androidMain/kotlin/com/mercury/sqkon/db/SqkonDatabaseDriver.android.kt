package com.mercury.sqkon.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory

/**
 * @param name The name of the database to open or create. If null, an in-memory database will
 *  be created, which will not persist across application restarts. Defaults to "sqkon.db".
 */
internal actual class DriverFactory(
    private val context: Context,
    private val name: String? = "sqkon.db"
) {
    actual fun createDriver(): SqlDriver {
        val helper = RequerySQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration(
                context = context,
                name = name,
                callback = AndroidSqliteDriver.Callback(SqkonDatabase.Schema.synchronous()),
                allowDataLossOnRecovery = true,
            )
        )
        return AndroidSqliteDriver(helper)
    }
}