package com.mercury.sqkon.db

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType


internal actual fun driverFactory(): DriverFactory {
    return DriverFactory(
        databaseType = AndroidxSqliteDatabaseType.Memory,
    )
}