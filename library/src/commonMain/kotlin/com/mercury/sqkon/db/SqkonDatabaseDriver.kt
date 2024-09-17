package com.mercury.sqkon.db

import app.cash.sqldelight.db.SqlDriver

internal expect class DriverFactory {
    fun createDriver(): SqlDriver
}

internal fun createEntityQueries(driverFactory: DriverFactory): EntityQueries {
    val driver = driverFactory.createDriver()
    return EntityQueries(driver)
}
