package com.mercury.sqkon.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory

internal actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        val helper = RequerySQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration(
                context = context,
                name = "sqkon.db",
                callback = AndroidSqliteDriver.Callback(SqkonDatabase.Schema.synchronous()),
                allowDataLossOnRecovery = true,
            )
        )
        return AndroidSqliteDriver(helper)
    }
}