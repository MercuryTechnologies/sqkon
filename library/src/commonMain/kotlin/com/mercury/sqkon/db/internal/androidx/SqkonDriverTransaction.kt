package com.mercury.sqkon.db.internal.androidx

import com.mercury.sqkon.db.internal.SqkonTransaction

/**
 * Concrete transaction handle bound to the writer connection of [AndroidxSqkonDriver].
 * Queues `afterCommit`/`afterRollback` hooks; on nested commit, hooks are propagated to the
 * enclosing transaction so they fire only at the outermost commit (or rollback).
 *
 * The driver owns the BEGIN/COMMIT/ROLLBACK SQL and writer-connection release — see
 * [AndroidxSqkonDriver.newTransaction] / [AndroidxSqkonDriver.endTransaction].
 */
internal class SqkonDriverTransaction(
    override val enclosingTransaction: SqkonDriverTransaction?,
) : SqkonTransaction() {

    private val onCommit = mutableListOf<() -> Unit>()
    private val onRollback = mutableListOf<() -> Unit>()

    override fun afterCommit(block: () -> Unit) { onCommit.add(block) }
    override fun afterRollback(block: () -> Unit) { onRollback.add(block) }

    /** Fire queued afterCommit hooks (call only at outermost commit). */
    fun runAfterCommit() { onCommit.forEach { it() } }

    /** Fire queued afterRollback hooks (call only at outermost rollback). */
    fun runAfterRollback() { onRollback.forEach { it() } }

    /** Move queued hooks from this nested transaction up to its enclosing tx. */
    fun propagateHooksTo(parent: SqkonDriverTransaction) {
        parent.onCommit.addAll(onCommit); onCommit.clear()
        parent.onRollback.addAll(onRollback); onRollback.clear()
    }
}
