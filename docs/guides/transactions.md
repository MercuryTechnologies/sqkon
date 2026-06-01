---
layout: default
title: Transactions
parent: Guides
nav_order: 7
---

# Transactions
{: .no_toc }

Sqkon gives every `KeyValueStorage<T>` a `transaction { … }` extension, so you
can wrap multiple writes — across one store or many — in a single atomic block.
The block runs against a Sqkon-owned `SqkonTransactionScope`; no SQLDelight types
are exposed.

{: .note }
**Changed in 2.0.** Stores no longer implement SQLDelight's `Transacter`. The
`transaction { }` / `transactionWithResult { }` calls below are unchanged, but if
you held a store as a `Transacter` or imported `TransactionCallbacks`, see
[Upgrading from 1.x](#upgrading-from-1x).

1. TOC
{:toc}

## When to use a transaction

Reach for an explicit `transaction { … }` block when you need:

- **Atomic multi-write** across stores ("debit one account, credit another"
  semantics).
- **A single Flow emission** for a logically-grouped set of writes — observers
  see one emission per *committed* transaction, not one per write.
- **Read-then-write** consistency where another writer must not slip in between
  the read and the write.

For single-row writes you don't need anything; the per-call methods are
already transactional.

## API

Two extension functions on `KeyValueStorage<T>` open a transaction; the lambda
receiver is [`SqkonTransactionScope`](#the-scope):

```kotlin
merchants.transaction {
    merchants.deleteAll()
    merchants.insertAll(fresh.associateBy { it.id })
}

// And the value-returning variant
val count = merchants.transactionWithResult {
    merchants.deleteAll()
    merchants.insertAll(fresh.associateBy { it.id })
    fresh.size
}
```

Two stores backed by the same `Sqkon` instance share a transactor — wrap them
together for cross-store atomicity:

```kotlin
merchants.transaction {
    merchants.upsert(merchant.id, merchant)
    transactions.upsert(txn.id, txn)
}
// Both writes commit as one unit; observers on either store see one emission.
```

### The scope

Inside the block you have a `SqkonTransactionScope`:

```kotlin
sealed interface SqkonTransactionScope {
    fun afterCommit(action: () -> Unit)
    fun afterRollback(action: () -> Unit)
    fun rollback(): Nothing
    fun transaction(body: SqkonTransactionScope.() -> Unit) // nested
}
```

- `afterCommit { }` / `afterRollback { }` — run a side effect once the **outermost**
  transaction settles (e.g. fire an analytics event only if the write stuck).
- `rollback()` — abort and discard the work. In `transaction { }` it returns
  silently; in `transactionWithResult { }` it throws `SqkonRollbackException`
  (there is no value to return). Either way the database transaction — and any
  enclosing one (there are no savepoints) — rolls back.
- `transaction { }` — a nested block; an inner `rollback()` rolls back the whole
  enclosing transaction.

```kotlin
merchants.transaction {
    merchants.upsert(merchant.id, merchant)
    if (!merchant.isValid) rollback() // nothing is written
    afterCommit { analytics.track("merchant_saved") }
}
```

## Upgrading from 1.x

`transaction { }` and `transactionWithResult { }` calls are **source-compatible** —
they resolve to the new extension functions unchanged. You only need to act if:

| 1.x | 2.0 |
|-----|-----|
| `val t: Transacter = store` (treating a store as a SQLDelight `Transacter`) | removed — call `store.transaction { … }` directly |
| `import app.cash.sqldelight.TransactionCallbacks` as the block receiver type | `SqkonTransactionScope` (usually implicit — just drop the import) |
| SQLDelight `Transacter` members beyond `afterCommit` / `afterRollback` / `rollback` / nested `transaction` | rework against `SqkonTransactionScope` |
| `transactionWithResult { rollback() }` returned the rollback value | now throws `SqkonRollbackException` — catch it if you relied on the return |

## What's already atomic

Every public write method on `KeyValueStorage` wraps its work in
`transaction { … }` internally. You do **not** need to add an outer
transaction for these:

- `insert`, `insertAll`
- `update`, `updateAll`
- `upsert`, `upsertAll`
- `delete` (by `Where`), `deleteByKey`, `deleteByKeys`, `deleteAll`
- `deleteExpired`, `deleteStale`

Wrapping them in your own `transaction { … }` is harmless — Sqkon nests
transactions — and is sometimes useful for grouping unrelated writes into one
observer emission.

## Flow emission timing

Flows emit **after** the transaction commits, never mid-transaction — observers
see one re-execution per committed block, regardless of how many writes it
contained. The full notification mechanism (and the bulk-write guarantees) lives
in [Reactive flows: when does it re-emit?]({{ '/guides/flow/#when-does-it-re-emit' | relative_url }}).

## Synchronous transactions

{: .important }
Since [PR #22](https://github.com/MercuryTechnologies/sqkon/pull/22)
(commit `444823c`), Sqkon's `transaction { … }` blocks run **synchronously**
on the calling thread. If you're upgrading
from a pre-`444823c` version that ran transactions on a write dispatcher,
move any `runBlocking { … }` or `withContext(Dispatchers.IO) { … }` you added
*around* `transaction` calls — the block already executes on whatever thread
called it.

The tradeoff is intentional: synchronous semantics make ordering predictable
and prevent the `ThreadLocal`-based transaction tracking from getting muddled
by suspension points. For long-running write batches, dispatch the entire
operation onto a background thread yourself.

## See also

- [Reactive flows]({{ '/guides/flow/' | relative_url }}) — when emissions fire
  relative to commits.
- Source: `library/src/commonMain/kotlin/com/mercury/sqkon/db/SqkonTransactionScope.kt`,
  `library/src/commonMain/kotlin/com/mercury/sqkon/db/Transactions.kt`,
  `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/SqkonTransacter.kt`.
