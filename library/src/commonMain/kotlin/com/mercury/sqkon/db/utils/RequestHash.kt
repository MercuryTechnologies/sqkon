package com.mercury.sqkon.db.utils

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class RequestHash(val hash: Int) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RequestHash>
}