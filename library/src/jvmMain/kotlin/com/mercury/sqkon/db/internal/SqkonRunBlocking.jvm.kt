package com.mercury.sqkon.db.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

internal actual fun <T> sqkonRunBlocking(block: suspend CoroutineScope.() -> T): T =
    runBlocking { block() }
