# Changelog

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
