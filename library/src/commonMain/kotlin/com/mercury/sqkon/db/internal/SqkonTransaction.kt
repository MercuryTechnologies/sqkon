package com.mercury.sqkon.db.internal

/**
 * Sqkon-owned transaction handle. Phase 2 mirrors the publicly reachable
 * surface of `app.cash.sqldelight.Transacter.Transaction`. Lifecycle methods
 * (`successful`, `endTransaction`) are intentionally absent — those are
 * SQLDelight-internal and managed inside `SqkonTransacter.transaction { }`.
 * Phase 5 fills in the gaps when we stop riding `TransacterImpl`.
 */
abstract class SqkonTransaction {
    abstract fun afterCommit(block: () -> Unit)
    abstract fun afterRollback(block: () -> Unit)
}
