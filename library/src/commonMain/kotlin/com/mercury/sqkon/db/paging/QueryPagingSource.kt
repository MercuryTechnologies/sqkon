package com.mercury.sqkon.db.paging

import androidx.paging.PagingSource
import com.mercury.sqkon.db.internal.SqkonQuery
import kotlin.coroutines.cancellation.CancellationException
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

    /**
     * Runs a `load` body, turning a failure into a `LoadResult` instead of throwing out of
     * `load()` (which can crash the collecting coroutine). [CancellationException] is rethrown so
     * structured concurrency is preserved — it must never be reported as a load error. If the
     * source was invalidated mid-load the result is [PagingSource.LoadResult.Invalid]. See #81.
     */
    protected inline fun loadResultCatching(
        block: () -> PagingSource.LoadResult<Key, Value>,
    ): PagingSource.LoadResult<Key, Value> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (invalid) PagingSource.LoadResult.Invalid() else PagingSource.LoadResult.Error(e)
    }

}
