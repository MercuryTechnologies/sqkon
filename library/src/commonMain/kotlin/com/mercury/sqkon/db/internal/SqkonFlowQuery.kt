package com.mercury.sqkon.db.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Sqkon-owned port of SQLDelight's `coroutines-extensions/FlowQuery.kt`,
 * retyped against `SqkonQuery`. Synchronous execute (Sqkon does not use
 * SQLDelight's `generateAsync = true` mode).
 */
internal fun <T : Any> SqkonQuery<T>.asFlow(): Flow<SqkonQuery<T>> = callbackFlow {
    val listener = SqkonQuery.Listener { trySend(this@asFlow) }
    addListener(listener)
    trySend(this@asFlow)
    awaitClose { removeListener(listener) }
}

internal fun <T : Any> Flow<SqkonQuery<T>>.mapToList(context: CoroutineDispatcher): Flow<List<T>> =
    map { it.executeAsList() }.flowOn(context)

internal fun <T : Any> Flow<SqkonQuery<T>>.mapToOne(context: CoroutineDispatcher): Flow<T> =
    map { it.executeAsOne() }.flowOn(context)

internal fun <T : Any> Flow<SqkonQuery<T>>.mapToOneNotNull(context: CoroutineDispatcher): Flow<T> =
    map { it.executeAsOneOrNull() ?: error("ResultSet returned null") }.flowOn(context)

internal fun <T : Any> Flow<SqkonQuery<T>>.mapToOneOrNull(context: CoroutineDispatcher): Flow<T?> =
    map { it.executeAsOneOrNull() }.flowOn(context)
