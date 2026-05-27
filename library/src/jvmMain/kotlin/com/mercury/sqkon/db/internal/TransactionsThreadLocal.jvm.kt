package com.mercury.sqkon.db.internal

internal actual class TransactionsThreadLocal actual constructor() {
    private val threadLocal = ThreadLocal<SqkonTransaction?>()
    actual fun get(): SqkonTransaction? = threadLocal.get()
    actual fun set(value: SqkonTransaction?) {
        if (value == null) threadLocal.remove() else threadLocal.set(value)
    }
}
