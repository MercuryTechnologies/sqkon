package com.mercury.sqkon.db.internal

import kotlinx.coroutines.CoroutineScope

/**
 * Bridges the suspending pool primitives (`Mutex`, `Channel`) into the synchronous SqkonDriver
 * interface. Available on JVM/Android/iOS via `kotlinx.coroutines.runBlocking`.
 */
internal expect fun <T> sqkonRunBlocking(block: suspend CoroutineScope.() -> T): T
