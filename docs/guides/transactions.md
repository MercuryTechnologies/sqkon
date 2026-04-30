---
layout: default
title: Transactions
parent: Guides
nav_order: 7
---

# Transactions
{: .no_toc }

Sqkon exposes SQLDelight's transaction API directly: `KeyValueStorage<T>`
implements `Transacter`, so you can wrap multiple writes — across one store or
many — in a single atomic block.

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

`KeyValueStorage<T>` is `Transacter by transacter`, which means the standard
SQLDelight transaction API is available on every store:

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

## What's already atomic

Every public write method on `KeyValueStorage` wraps its work in
`transaction { … }` internally. You do **not** need to add an outer
transaction for these:

- `insert`, `insertAll`
- `update`, `updateAll`
- `upsert`, `upsertAll`
- `delete` (by `Where`), `deleteByKey`, `deleteByKeys`, `deleteAll`
- `deleteExpired`, `deleteStale`

Wrapping them in your own `transaction { … }` is harmless — SQLDelight nests —
and is sometimes useful for grouping unrelated writes into one observer
emission.

## Flow emission timing

Flows emit **after** the transaction commits, never mid-transaction — observers
see one re-execution per committed block, regardless of how many writes it
contained. The full notification mechanism (and the bulk-write guarantees) lives
in [Reactive flows: when does it re-emit?]({{ '/guides/flow/#when-does-it-re-emit' | relative_url }}).

## Synchronous transactions

{: .important }
Since [PR #22](https://github.com/MercuryTechnologies/sqkon/pull/22)
(commit `444823c`), Sqkon's `transaction { … }` blocks run **synchronously**
on the calling thread, the same as SQLDelight upstream. If you're upgrading
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
- Upstream API: SQLDelight
  [Transacter](https://cashapp.github.io/sqldelight/2.0.0/jvm_sqlite/transactions/).
- Source: `library/src/commonMain/kotlin/com/mercury/sqkon/db/utils/SqkonTransacter.kt`,
  `library/src/commonMain/kotlin/com/mercury/sqkon/db/KeyValueStorage.kt`.
