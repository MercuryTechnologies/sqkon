---
layout: default
title: Paging list with Compose
parent: Examples
nav_order: 3
---

# Paging list with Compose

For lists that may grow into the thousands, you don't want `selectAll()` —
you want paged reads. Sqkon ships a keyset `PagingSource` that integrates
directly with AndroidX Paging 3 and Compose's `LazyColumn`. Pages have
constant cost regardless of how far the user scrolls.

## Dependency

Add the Paging Compose artifact alongside Sqkon. Use the latest 3.3.x:

```kotlin
dependencies {
    implementation("androidx.paging:paging-compose:3.3.x")
    implementation("com.mercury.sqkon:library:<version>")
}
```

## The data class

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class Merchant(
    val id: String,
    val name: String,
    val category: String,
)
```

## The ViewModel

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.mercury.sqkon.db.KeyValueStorage
import com.mercury.sqkon.db.OrderBy
import kotlinx.coroutines.flow.Flow

class MerchantListViewModel(
    private val storage: KeyValueStorage<Merchant>,
) : ViewModel() {

    val pagedMerchants: Flow<PagingData<Merchant>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            prefetchDistance = 10,
            initialLoadSize = 20,
        ),
    ) {
        storage.selectKeysetPagingSource(
            pageSize = 20,
            orderBy = listOf(OrderBy(Merchant::name)),
        )
    }.flow.cachedIn(viewModelScope)
}
```

The `pageSize` you pass to `selectKeysetPagingSource` should match
`PagingConfig.pageSize`. Sqkon precomputes page boundary keys once per
`PagingSource` lifetime — when the underlying data changes, the source is
invalidated and a fresh set of boundaries is computed.

{: .note }
Keyset paging has `jumpingSupported = false`. The Pager always loads pages
sequentially from the start (or from a saved refresh key). For random access
on huge lists, see `selectPagingSource` (offset-based) — but pay the price of
`O(n)` SQL `OFFSET` on the cold pages.

## The Composable

```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey

@Composable
fun MerchantList(viewModel: MerchantListViewModel = viewModel()) {
    val items = viewModel.pagedMerchants.collectAsLazyPagingItems()

    LazyColumn {
        items(
            count = items.itemCount,
            key = items.itemKey { it.id },
        ) { index ->
            items[index]?.let { MerchantRow(it) }
        }

        when (val state = items.loadState.append) {
            is LoadState.Loading -> item {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            is LoadState.Error -> item {
                Text(
                    text = "Failed to load: ${state.error.message}",
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun MerchantRow(merchant: Merchant) {
    Text(text = merchant.name, modifier = Modifier.padding(16.dp))
}
```

`items.itemKey { it.id }` is essential — without a stable key, scrolling
back to a recomposed item discards its state and the list flickers when new
pages land.

## Filtering and sorting

`selectKeysetPagingSource` accepts the same `where` and `orderBy` arguments
as `select`:

```kotlin
storage.selectKeysetPagingSource(
    pageSize = 20,
    where = Merchant::category eq "Restaurant",
    orderBy = listOf(OrderBy(Merchant::name)),
)
```

When the user changes the filter in the UI, swap the `Pager` for a new one
and let Compose recompose — the previous `PagingSource` will be cancelled.

{: .warning }
Don't reuse a `Pager` instance across filter changes. The `PagingSource` is
constructed inside the `Pager` lambda; rebuild the `Pager` when inputs change
(commonly: hold the inputs in `StateFlow` and `flatMapLatest` to a fresh
`Pager.flow` each time).

## Where to go next

- [Paging guide]({{ '/guides/paging/' | relative_url }}) — keyset vs. offset trade-offs.
- [Reactive search]({{ '/examples/search-feature/' | relative_url }}) — combining paging with a debounced query.
