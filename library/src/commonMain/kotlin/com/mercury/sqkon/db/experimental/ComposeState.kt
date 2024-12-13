package com.mercury.sqkon.db.experimental

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import app.cash.sqldelight.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.suspendCoroutine


suspend fun <T : Any> Query<T>.asState(scope: CoroutineScope): State<Query<T>> {
    val channel = Channel<Unit>(CONFLATED)
    channel.trySend(Unit)
    val listener = Query.Listener { channel.trySend(Unit) }
    val state: MutableState<Query<T>> = mutableStateOf(this)
    scope.launch {
        addListener(listener)
        try {
            for (item in channel) {
                state.value = this@asState
            }
        } finally {
            removeListener(listener)
        }
    }
    return state
}

/*
@JvmName("toFlow")
fun <T : Any> Query<T>.asFlow(): Flow<Query<T>> = flow {
  val channel = Channel<Unit>(CONFLATED)
  channel.trySend(Unit)

  val listener = Query.Listener {
    channel.trySend(Unit)
  }

  addListener(listener)
  try {
    for (item in channel) {
      emit(this@asFlow)
    }
  } finally {
    removeListener(listener)
  }
}
 */