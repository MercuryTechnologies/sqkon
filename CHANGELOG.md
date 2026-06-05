# Changelog

## [3.0.0](https://github.com/MercuryTechnologies/sqkon/compare/2.1.0...3.0.0) (2026-06-05)


### ⚠ BREAKING CHANGES

* strip unused sqldelight plugin + runtime deps (3.0.0 cleanup) ([#61](https://github.com/MercuryTechnologies/sqkon/issues/61))
* Sqkon factories on JVM and Android no longer accept com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType; use com.mercury.sqkon.db.SqkonDatabaseType (Memory / FileBacked) instead. EntityQueries and MetadataQueries constructors are now internal — instances are created exclusively via the Sqkon(...) factories.
* KeyValueStorage no longer implements app.cash.sqldelight.Transacter, and the transaction lambda receiver is SqkonTransactionScope instead of SQLDelight's TransactionCallbacks. transaction { } / transactionWithResult { } calls are source-compatible. Update code only if you: (1) held a store as a Transacter (`val t: Transacter = store`) — call store.transaction { } directly; (2) imported app.cash.sqldelight.TransactionCallbacks as the block receiver type — drop the import, SqkonTransactionScope is inferred; (3) used Transacter members beyond afterCommit/afterRollback/rollback/nested transaction — rework against SqkonTransactionScope; (4) relied on transactionWithResult { rollback() } returning a value — it now throws SqkonRollbackException. See docs/guides/transactions.md#upgrading-from-1x.

### Features

* **arch:** internal SqkonDriver abstraction (MOB-3289) ([#55](https://github.com/MercuryTechnologies/sqkon/issues/55)) ([eca9a34](https://github.com/MercuryTechnologies/sqkon/commit/eca9a34632c36c1ef20445f611eb9ba90fcf1b18))
* direct androidx.sqlite driver, drop SQLDelight SqlDriver (MOB-3293) ([#60](https://github.com/MercuryTechnologies/sqkon/issues/60)) ([fe88ce8](https://github.com/MercuryTechnologies/sqkon/commit/fe88ce8a49ff2c17aeb278b7335686cc636fe0b7))
* hand-roll entity and metadata data classes (MOB-3290) ([#57](https://github.com/MercuryTechnologies/sqkon/issues/57)) ([2c9afeb](https://github.com/MercuryTechnologies/sqkon/commit/2c9afeb7eefa25ce6e15d452f2fad36a0aa6cdac))
* hide sqldelight transacter behind SqkonTransactionScope (MOB-3292) ([#59](https://github.com/MercuryTechnologies/sqkon/issues/59)) ([0255188](https://github.com/MercuryTechnologies/sqkon/commit/025518825236dee8e48f0a6463c95b4a18fd5c4f))


### Bug Fixes

* alias SqlException to androidx.sqlite.SQLiteException so catches fire ([#82](https://github.com/MercuryTechnologies/sqkon/issues/82)) ([#109](https://github.com/MercuryTechnologies/sqkon/issues/109)) ([10e2a80](https://github.com/MercuryTechnologies/sqkon/commit/10e2a80ffaaa87ce76d8eeac65f4006d885a18c2))
* bind Float query values as reals instead of truncating to Long ([#72](https://github.com/MercuryTechnologies/sqkon/issues/72)) ([#98](https://github.com/MercuryTechnologies/sqkon/issues/98)) ([96cba5d](https://github.com/MercuryTechnologies/sqkon/commit/96cba5d281787b527f1ddb3f22124bed8610dea6))
* consistent paging load() error handling — return Error, rethrow Cancellation ([#81](https://github.com/MercuryTechnologies/sqkon/issues/81)) ([#108](https://github.com/MercuryTechnologies/sqkon/issues/108)) ([e2fa920](https://github.com/MercuryTechnologies/sqkon/commit/e2fa920d073b50fa00166161187164d468911d69))
* count(where=…) over-counts list/multi-match predicates ([#68](https://github.com/MercuryTechnologies/sqkon/issues/68)) ([#91](https://github.com/MercuryTechnologies/sqkon/issues/91)) ([4b295b1](https://github.com/MercuryTechnologies/sqkon/commit/4b295b112e8f663b38ccfe6233d9255bbeea0b5a))
* escape LIKE wildcards + match json_tree key quoting in fullkey predicates ([#69](https://github.com/MercuryTechnologies/sqkon/issues/69)) ([#93](https://github.com/MercuryTechnologies/sqkon/issues/93)) ([e6715ba](https://github.com/MercuryTechnologies/sqkon/commit/e6715ba61a3ed16611316d58b5d088ecb405501f))
* expose public-API deps as `api` so consumers get them transitively ([#78](https://github.com/MercuryTechnologies/sqkon/issues/78)) ([#104](https://github.com/MercuryTechnologies/sqkon/issues/104)) ([b9a55b7](https://github.com/MercuryTechnologies/sqkon/commit/b9a55b772dff07ea0000497af1c92c9524fef99d))
* guard ListenerIdentityMap with a Mutex for concurrent Flow collectors ([#76](https://github.com/MercuryTechnologies/sqkon/issues/76)) ([#102](https://github.com/MercuryTechnologies/sqkon/issues/102)) ([ac6b0b7](https://github.com/MercuryTechnologies/sqkon/commit/ac6b0b73e9447dd75ae7b2b880e888af771477be))
* make schema migration stepwise/exhaustive + reject downgrades ([#77](https://github.com/MercuryTechnologies/sqkon/issues/77)) ([#103](https://github.com/MercuryTechnologies/sqkon/issues/103)) ([30e51b0](https://github.com/MercuryTechnologies/sqkon/commit/30e51b0ae90cb7641cbb0c17c2f5935731d79020))
* parenthesize expiry predicate so it can't leak rows across stores ([#67](https://github.com/MercuryTechnologies/sqkon/issues/67)) ([#90](https://github.com/MercuryTechnologies/sqkon/issues/90)) ([700c696](https://github.com/MercuryTechnologies/sqkon/commit/700c696b7e25b109e9f7f150506421e8012fb687))
* set PRAGMA busy_timeout on every connection (WAL contention hardening) ([#75](https://github.com/MercuryTechnologies/sqkon/issues/75)) ([#101](https://github.com/MercuryTechnologies/sqkon/issues/101)) ([30fbd65](https://github.com/MercuryTechnologies/sqkon/commit/30fbd65ac85638bc5ceb1f2b489aec4745b622d8))
* verify SQL on statement-cache hit to guard against identifier collisions ([#74](https://github.com/MercuryTechnologies/sqkon/issues/74)) ([#100](https://github.com/MercuryTechnologies/sqkon/issues/100)) ([686276f](https://github.com/MercuryTechnologies/sqkon/commit/686276f7cfe11d345f6c45bce3b7f10158f9780b))


### Dependencies

* migrate to kotlin.time.Instant, drop kotlinx-datetime (MOB-3549) ([#63](https://github.com/MercuryTechnologies/sqkon/issues/63)) ([c1e2be5](https://github.com/MercuryTechnologies/sqkon/commit/c1e2be599d46d0077289c52a317f0943bed07092))


### Documentation

* correct keyset paging cost — O(n) ordered scan per page, not O(log n) ([#80](https://github.com/MercuryTechnologies/sqkon/issues/80)) ([#106](https://github.com/MercuryTechnologies/sqkon/issues/106)) ([8dc3c5e](https://github.com/MercuryTechnologies/sqkon/commit/8dc3c5e3b00ef52c09969ff19b6c211263833a6f))
* correct write-threading claim — writes block the caller thread ([#70](https://github.com/MercuryTechnologies/sqkon/issues/70)) ([#96](https://github.com/MercuryTechnologies/sqkon/issues/96)) ([e60e018](https://github.com/MercuryTechnologies/sqkon/commit/e60e018e9dfcbe8d75d1f0005e82a93cf4917b50))
* document the enum @SerialName query gap accurately + drop dead code ([#73](https://github.com/MercuryTechnologies/sqkon/issues/73)) ([#99](https://github.com/MercuryTechnologies/sqkon/issues/99)) ([8e6a7a8](https://github.com/MercuryTechnologies/sqkon/commit/8e6a7a88edcb65ef2299af0a8e6f8d93fdd3d741))
* fix non-compiling README/KDoc snippets + stale install version ([#79](https://github.com/MercuryTechnologies/sqkon/issues/79)) ([#105](https://github.com/MercuryTechnologies/sqkon/issues/105)) ([3a1867c](https://github.com/MercuryTechnologies/sqkon/commit/3a1867c3ca65999bfbbec04c8ee44cdccf3eb5bc))
* keyset first-page cost is O(n) too, not O(pageSize) ([#80](https://github.com/MercuryTechnologies/sqkon/issues/80) follow-up) ([#107](https://github.com/MercuryTechnologies/sqkon/issues/107)) ([7ba6761](https://github.com/MercuryTechnologies/sqkon/commit/7ba676157fbc5fd3f84422e02386795dfb9d32cf))


### Miscellaneous

* strip unused sqldelight plugin + runtime deps (3.0.0 cleanup) ([#61](https://github.com/MercuryTechnologies/sqkon/issues/61)) ([f4e975b](https://github.com/MercuryTechnologies/sqkon/commit/f4e975bec979d6b326a4cbaad7936b0bf1ceab1c))

## [2.1.0](https://github.com/MercuryTechnologies/sqkon/compare/2.0.0...2.1.0) (2026-05-13)


### Features

* ios scaffold + SqkonDispatchers refactor (MOB-3288) ([#50](https://github.com/MercuryTechnologies/sqkon/issues/50)) ([5f7ce58](https://github.com/MercuryTechnologies/sqkon/commit/5f7ce581e789fcdf64a9c09c1c6114619bb74a3a))


### Bug Fixes

* keyset paging survives mediator writes mid-load ([#53](https://github.com/MercuryTechnologies/sqkon/issues/53)) ([b2dae78](https://github.com/MercuryTechnologies/sqkon/commit/b2dae78b306381fc5b0c23700830ee0e844356b0))

## [2.0.0](https://github.com/MercuryTechnologies/sqkon/compare/1.3.2...2.0.0) (2026-05-12)


### ⚠ BREAKING CHANGES

* OrderBy<T> changed from a data class to a sealed class. Code that read OrderBy.path or used data-class operations (copy, componentN) must migrate to the new JsonPathOrderBy / CaseOrderBy subclasses.

### Features

* CASE/WHEN value-selection + predicate-dispatch (MOB-1627) ([#44](https://github.com/MercuryTechnologies/sqkon/issues/44)) ([6b16367](https://github.com/MercuryTechnologies/sqkon/commit/6b16367682925da3f15382da25de15e22d94306a))


### Bug Fixes

* **docs:** repair feature cards, swap in real logo, add CI smoke check ([#46](https://github.com/MercuryTechnologies/sqkon/issues/46)) ([546bb29](https://github.com/MercuryTechnologies/sqkon/commit/546bb294a8194b9c712a74e216d4b71d0d63b888))
* enable pretty permalinks so child pages resolve on GitHub Pages ([#43](https://github.com/MercuryTechnologies/sqkon/issues/43)) ([8f9b312](https://github.com/MercuryTechnologies/sqkon/commit/8f9b31269553c945d8199dfae355a3e87b69b2e6))
* keyset paging refresh key returns null on fresh source ([#51](https://github.com/MercuryTechnologies/sqkon/issues/51)) ([d29e325](https://github.com/MercuryTechnologies/sqkon/commit/d29e3259ea8f149fd6f3ab573f15f682247532b1))
* Not predicate inverts per-entity (was broken by json_tree row explosion) ([#48](https://github.com/MercuryTechnologies/sqkon/issues/48)) ([79b6e66](https://github.com/MercuryTechnologies/sqkon/commit/79b6e668dcdacfc63fa58e7529ea818e2c30165b))


### Dependencies

* bump kotlin, serialization, paging; refresh tooling and CI actions ([#40](https://github.com/MercuryTechnologies/sqkon/issues/40)) ([f6148af](https://github.com/MercuryTechnologies/sqkon/commit/f6148af4e45185a47fb8582b0f7e8ea608e154a0))


### Documentation

* GitHub Pages site (Jekyll + Just the Docs + Dokka v2) ([#42](https://github.com/MercuryTechnologies/sqkon/issues/42)) ([0ecfdf5](https://github.com/MercuryTechnologies/sqkon/commit/0ecfdf5f815738b3c69a0005864d2bcf812b0bc2))
* polish home page, restructure Querying, fix mermaid, link Why Sqkon cards ([#45](https://github.com/MercuryTechnologies/sqkon/issues/45)) ([ff6b043](https://github.com/MercuryTechnologies/sqkon/commit/ff6b0433076e36470e208a6ded1dac3ef0c79801))

## [1.3.2](https://github.com/MercuryTechnologies/sqkon/compare/1.3.1...1.3.2) (2026-04-28)


### Bug Fixes

* keyset paging invalidation on empty start + paging docs ([#38](https://github.com/MercuryTechnologies/sqkon/issues/38)) ([170d437](https://github.com/MercuryTechnologies/sqkon/commit/170d4379e57da25d8ab15e8c5651ffb06d6b2a52))

## [1.3.1](https://github.com/MercuryTechnologies/sqkon/compare/1.3.0...1.3.1) (2026-04-15)


### Bug Fixes

* trigger Maven Central deploy from release-please workflow ([945bb8b](https://github.com/MercuryTechnologies/sqkon/commit/945bb8bfc40799b15e305a833d903adfbfdba6bd))

## [1.3.0](https://github.com/MercuryTechnologies/sqkon/compare/1.2.0...1.3.0) (2026-04-15)


### Features

* automate releases with release-please ([#34](https://github.com/MercuryTechnologies/sqkon/issues/34)) ([077d7d1](https://github.com/MercuryTechnologies/sqkon/commit/077d7d1aed7c336c2d1815ac953f6b8a87f1d6ac))


### Bug Fixes

* pin release-please-action to correct SHA for v4.4.1 ([#35](https://github.com/MercuryTechnologies/sqkon/issues/35)) ([77294aa](https://github.com/MercuryTechnologies/sqkon/commit/77294aae3c42ec77ca9692bebb40ce35e512f471))
