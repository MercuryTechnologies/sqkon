package com.mercury.sqkon.db

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Read/write coroutine dispatcher bundle for Sqkon.
 *
 * Override at construction time for deterministic tests (e.g. with `StandardTestDispatcher`)
 * or to share an existing pool with the host application.
 */
class SqkonDispatchers(
    val read: CoroutineDispatcher,
    val write: CoroutineDispatcher,
)
