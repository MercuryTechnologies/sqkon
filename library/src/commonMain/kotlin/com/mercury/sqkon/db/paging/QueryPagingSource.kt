package com.mercury.sqkon.db.paging

import androidx.paging.PagingSource
import app.cash.sqldelight.Query
import kotlin.properties.Delegates

internal abstract class QueryPagingSource<Key : Any, Value : Any> : PagingSource<Key, Value>(),
    Query.Listener {

    protected var currentQuery: Query<*>? by Delegates.observable(null) { _, old, new ->
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
