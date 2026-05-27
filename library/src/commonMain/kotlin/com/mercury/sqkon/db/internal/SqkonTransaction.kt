package com.mercury.sqkon.db.internal

/**
 * Sqkon-owned transaction handle. Phase 3 mirrored only the user-visible part of SQLDelight's
 * `Transacter.Transaction` (afterCommit/afterRollback). Phase 6 adds the lifecycle pieces that the
 * native `AndroidxSqkonDriver` + new `SqkonTransacter` need: enclosing-tx pointer, success flags,
 * hook queues. The legacy SQLDelight bridge subclass (deleted in Phase 6 Task 12) doesn't need
 * these — defaults keep it compiling.
 */
internal abstract class SqkonTransaction {
    /** Enclosing transaction (when nested) or `null` for top-level. */
    open val enclosingTransaction: SqkonTransaction? = null

    /** Set by the transacter when the body returned without throwing. */
    var successful: Boolean = false
    /** Reset to `false` by a child rollback so the enclosing commit is downgraded to rollback. */
    var childrenSuccessful: Boolean = true

    abstract fun afterCommit(block: () -> Unit)
    abstract fun afterRollback(block: () -> Unit)

    /**
     * End this transaction. For top-level transactions this issues COMMIT (if [successful] and
     * [childrenSuccessful]) or ROLLBACK, fires the queued hooks, and releases the writer connection.
     * For nested transactions it propagates state + hooks to [enclosingTransaction]. The legacy
     * SQLDelight bridge overrides this as a no-op (its body wrapper manages SQLDelight's lifecycle).
     */
    abstract fun endTransaction()
}
