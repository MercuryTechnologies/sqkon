package com.mercury.sqkon.db

import app.cash.sqldelight.ColumnAdapter

class NoopColumnAdapter<T : Any, S> : ColumnAdapter<T, S> {
    override fun decode(databaseValue: S): T = TODO("Do not use adapters for this type")
    override fun encode(value: T): S {
        TODO("Do not use adapters for this type")
    }
}