package com.mercury.sqkon.db.utils

import kotlin.time.Clock

/**
 * Now in milliseconds. Unix epoch.
 *
 * TODO pass Clock through from the builder for testing
 */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()