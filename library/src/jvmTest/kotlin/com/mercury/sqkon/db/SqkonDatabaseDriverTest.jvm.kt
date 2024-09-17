package com.mercury.sqkon.db


fun createEntityQueries(): EntityQueries {
    return createEntityQueries(DriverFactory())
}