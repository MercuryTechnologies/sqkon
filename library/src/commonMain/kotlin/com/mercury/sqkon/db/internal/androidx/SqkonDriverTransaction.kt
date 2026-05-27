package com.mercury.sqkon.db.internal.androidx

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.mercury.sqkon.db.internal.SqkonTransaction
import com.mercury.sqkon.db.internal.TransactionsThreadLocal

/**
 * Concrete transaction handle bound to the writer connection of [AndroidxSqkonDriver].
 *
 * - **Top-level:** BEGIN IMMEDIATE issued by the driver on construction; COMMIT or ROLLBACK +
 *   writer release issued by [endTransaction].
 * - **Nested:** no SQL issued; [endTransaction] only propagates `successful`/`childrenSuccessful`
 *   and queued hooks to [enclosingTransaction]. A nested rollback (caught by the transacter) sets
 *   the enclosing's `childrenSuccessful = false` so its eventual outermost COMMIT downgrades to
 *   ROLLBACK (no savepoint semantics — matches SQLDelight `TransacterImpl`).
 */
internal class SqkonDriverTransaction(
    override val enclosingTransaction: SqkonDriverTransaction?,
    val connection: SQLiteConnection,
    private val transactions: TransactionsThreadLocal,
    private val onTopLevelRelease: () -> Unit,
) : SqkonTransaction() {

    private val onCommit = mutableListOf<() -> Unit>()
    private val onRollback = mutableListOf<() -> Unit>()

    override fun afterCommit(block: () -> Unit) { onCommit.add(block) }
    override fun afterRollback(block: () -> Unit) { onRollback.add(block) }

    override fun endTransaction() {
        val enclosing = enclosingTransaction
        // Pop thread-local back to the enclosing tx (or null for top-level).
        transactions.set(enclosing)
        if (enclosing != null) {
            // Nested: don't COMMIT/ROLLBACK; merge state + hooks into the enclosing tx.
            if (!successful || !childrenSuccessful) enclosing.childrenSuccessful = false
            enclosing.onCommit.addAll(onCommit); onCommit.clear()
            enclosing.onRollback.addAll(onRollback); onRollback.clear()
            return
        }
        val didCommit = successful && childrenSuccessful
        try {
            if (didCommit) connection.execSQL("COMMIT") else connection.execSQL("ROLLBACK")
        } finally {
            // Release the writer BEFORE firing hooks. Hooks (e.g. afterCommit { ... reading the
            // db }) may need to re-acquire reader/writer connections; holding the writer mutex
            // here would deadlock the same coroutine.
            onTopLevelRelease()
        }
        if (didCommit) onCommit.forEach { it() } else onRollback.forEach { it() }
    }
}
