---
layout: default
title: Reactive search
parent: Examples
nav_order: 4
---

# Reactive search

Search-as-you-type pairs nicely with Sqkon's Flow-based reads: hold the query
text in a `StateFlow`, switch to a fresh result `Flow` whenever the text
changes, and let Compose collect it. With a `debounce` in the middle you avoid
hammering SQLite on every keystroke.

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
import com.mercury.sqkon.db.KeyValueStorage
import com.mercury.sqkon.db.like
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MerchantSearchViewModel(
    private val storage: KeyValueStorage<Merchant>,
) : ViewModel() {

    val query = MutableStateFlow("")

    val results: StateFlow<List<Merchant>> = query
        .debounce(300.milliseconds)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                storage.selectAll()
            } else {
                storage.select(where = Merchant::name like "%$q%")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

A few things to notice:

- `flatMapLatest` cancels the previous SQL subscription whenever the query
  changes. You never have a stale page of results racing the new one.
- `debounce(300.milliseconds)` swallows mid-typing values. Pick a number
  that feels snappy on your dataset — 200–400 ms is the usual range.
- A blank query falls through to `selectAll()` rather than running a
  pointless `LIKE '%%'`.

## The Composable

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MerchantSearch(viewModel: MerchantSearchViewModel = viewModel()) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            label = { Text("Search merchants") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        LazyColumn {
            items(results, key = { it.id }) { merchant ->
                Text(
                    text = merchant.name,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}
```

That's the whole feature. The `TextField` writes into `query`; the
`StateFlow` emits a debounced value; `flatMapLatest` swaps to a new
`select(...)` Flow; the new rows render. No imperative refresh, no manual
cancellation.

## Performance caveats

`Merchant::name like "%foo%"` matches anywhere in the string — convenient,
but a leading wildcard means SQLite can't use an index. For each keystroke
SQLite scans every row in the entity, parses its JSON, and applies the
predicate. Up to a few thousand rows, this is fine. Past that you'll feel it.

{: .warning }
`like '%foo%'` (leading wildcard) defeats indexing — it's a full table
scan. For larger datasets, denormalize a lowercase `searchTokens` field into
your model (`val searchTokens: String = "$name $category".lowercase()`) and
match with `like "$q%"`, which **can** use a prefix index. See
[Performance]({{ '/guides/performance/' | relative_url }}) for the full
story.

## Combining with paging

For long search results, pair this pattern with the
[Paging recipe]({{ '/examples/paging-list/' | relative_url }}). Hold the
debounced query in a `StateFlow`, then `flatMapLatest` to a fresh
`Pager.flow` each time it changes — the old paging source is cancelled when
the new query arrives.

## Where to go next

- [Querying guide]({{ '/guides/querying/' | relative_url }}) — every
  operator, including `inList`, `notInList`, and `not(...)`.
- [Performance]({{ '/guides/performance/' | relative_url }}) — how `like`
  interacts with indexes (or doesn't).
