package com.mercury.sqkon.db.utils

import kotlinx.datetime.Clock

/**
 * Now in milliseconds. Unix epoch.
 */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()