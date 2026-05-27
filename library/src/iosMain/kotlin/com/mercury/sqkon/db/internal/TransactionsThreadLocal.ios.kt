package com.mercury.sqkon.db.internal

import kotlin.native.concurrent.ThreadLocal

/**
 * iOS holder. The write dispatcher is single-threaded on iOS, so a single per-thread slot is
 * sufficient. `@ThreadLocal` makes the var truly thread-isolated on Kotlin/Native.
 */
@ThreadLocal
private var currentTransaction: SqkonTransaction? = null

internal actual class TransactionsThreadLocal actual constructor() {
    actual fun get(): SqkonTransaction? = currentTransaction
    actual fun set(value: SqkonTransaction?) { currentTransaction = value }
}
