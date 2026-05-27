package com.mercury.sqkon.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import com.mercury.sqkon.db.internal.SqkonDriver
import com.mercury.sqkon.db.internal.androidx.AndroidxSqkonDriver
import com.mercury.sqkon.db.internal.androidx.SqkonConnectionFactory
import com.mercury.sqkon.db.internal.schema.SqkonDatabaseSchema
import kotlinx.coroutines.Dispatchers

internal actual const val connectionPoolSize = 4

@PublishedApi
internal actual val defaultSqkonDispatchers: SqkonDispatchers by lazy {
    SqkonDispatchers(
        read = Dispatchers.IO.limitedParallelism(connectionPoolSize),
        write = Dispatchers.IO.limitedParallelism(1),
    )
}

internal actual class DriverFactory(
    private val type: SqkonDatabaseType = SqkonDatabaseType.Memory,
    private val config: SqkonDriverConfig = SqkonDriverConfig(readerConnections = connectionPoolSize),
) {
    actual fun createDriver(): SqkonDriver {
        val bundled = BundledSQLiteDriver()
        // JVM now explicitly sets SQLITE_OPEN_FULLMUTEX (previously the eygraber driver opened
        // connections without it on JVM; on Android it was already set explicitly).
        val factory = SqkonConnectionFactory { name ->
            bundled.open(name, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX)
        }
        val (name, isMemory) = when (type) {
            SqkonDatabaseType.Memory -> ":memory:" to true
            is SqkonDatabaseType.FileBacked -> type.path to false
        }
        return AndroidxSqkonDriver(factory, name, isMemory, SqkonDatabaseSchema, config)
    }
}
