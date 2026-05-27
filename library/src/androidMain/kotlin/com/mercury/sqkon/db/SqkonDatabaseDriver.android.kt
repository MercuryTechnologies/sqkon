package com.mercury.sqkon.db

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import com.mercury.sqkon.db.internal.SqkonDriver
import com.mercury.sqkon.db.internal.androidx.AndroidxSqkonDriver
import com.mercury.sqkon.db.internal.androidx.SqkonConnectionFactory
import com.mercury.sqkon.db.internal.schema.SqkonDatabaseSchema
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newFixedThreadPoolContext

internal actual val connectionPoolSize: Int by lazy { getWALConnectionPoolSize() }

@OptIn(DelicateCoroutinesApi::class)
@PublishedApi
internal actual val defaultSqkonDispatchers: SqkonDispatchers by lazy {
    SqkonDispatchers(
        read = Dispatchers.IO.limitedParallelism(connectionPoolSize),
        write = newFixedThreadPoolContext(nThreads = 1, "SqkonWriteDispatcher"),
    )
}

internal actual class DriverFactory(
    private val context: Context,
    private val type: SqkonDatabaseType =
        SqkonDatabaseType.FileBacked(context.getDatabasePath("sqkon.db").absolutePath),
    private val config: SqkonDriverConfig = SqkonDriverConfig(readerConnections = connectionPoolSize),
) {
    actual fun createDriver(): SqkonDriver {
        val bundled = BundledSQLiteDriver()
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

@SuppressLint("DiscouragedApi")
private fun getWALConnectionPoolSize(): Int {
    val resources = Resources.getSystem()
    val resId = resources.getIdentifier("db_connection_pool_size", "integer", "android")
    return if (resId != 0) resources.getInteger(resId) else 4
}
