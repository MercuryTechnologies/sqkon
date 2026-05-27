package com.mercury.sqkon.db.internal

/** Thread-confined holder for the active driver transaction. */
internal expect class TransactionsThreadLocal() {
    fun get(): SqkonTransaction?
    fun set(value: SqkonTransaction?)
}
