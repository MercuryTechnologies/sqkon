package com.mercury.sqkon.db.paging

import androidx.paging.PagingSource
import com.mercury.sqkon.db.internal.SqkonQuery
import kotlin.properties.Delegates

internal abstract class QueryPagingSource<Key : Any, Value : Any> : PagingSource<Key, Value>(),
    SqkonQuery.Listener {

    protected var currentQuery: SqkonQuery<*>? by Delegates.observable(null) { _, old, new ->
        old?.removeListener(this)
        new?.addListener(this)
    }

    init {
        registerInvalidatedCallback {
            currentQuery?.removeListener(this)
            currentQuery = null
        }
    }

    final override fun queryResultsChanged() = invalidate()

}
