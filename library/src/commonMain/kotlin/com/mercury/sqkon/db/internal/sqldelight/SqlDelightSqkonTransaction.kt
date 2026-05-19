package com.mercury.sqkon.db.internal.sqldelight

import app.cash.sqldelight.Transacter
import com.mercury.sqkon.db.internal.SqkonTransaction

internal class SqlDelightSqkonTransaction(
    internal val delegate: Transacter.Transaction,
) : SqkonTransaction() {
    override fun afterCommit(block: () -> Unit) { delegate.afterCommit(block) }
    override fun afterRollback(block: () -> Unit) { delegate.afterRollback(block) }
}
