package com.mercury.sqkon.db


actual fun createEntityQueries(): EntityQueries {
    return createEntityQueries(DriverFactory())
}
