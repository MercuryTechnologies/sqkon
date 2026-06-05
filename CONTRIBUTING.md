# Contributing to Sqkon

Sqkon is a Kotlin Multiplatform key-value storage library maintained by [Mercury](https://mercury.com). External contributions are welcome — bug reports, documentation fixes, and pull requests of any size. This guide covers the workflow, conventions, and project rules you'll need before opening a PR.

## Quick start

1. Fork the repo and clone your fork.
2. Run the test suite to confirm a clean baseline:
   ```bash
   ./gradlew jvmTest
   ```
3. Make your change and add tests covering the new behavior.
4. Commit using [Conventional Commits](#commit-format).
5. Push and open a pull request against `main`.

CI runs JVM and Android instrumented tests on every PR — see the [CI](#ci) section.

## Commit format

Sqkon uses [Conventional Commits](https://www.conventionalcommits.org/). Release-please reads commit messages on `main` to determine the next version bump and to generate the changelog automatically.

| Prefix             | Version bump | Example                                           |
| ------------------ | ------------ | ------------------------------------------------- |
| `feat:`            | minor        | `feat: add keyset paging support`                 |
| `fix:`             | patch        | `fix: null handling in JsonPath`                  |
| `feat!:` / `fix!:` | **major**    | `feat!: remove deprecated expiry API`             |
| `perf:`            | patch        | `perf: optimize JSONB query plan`                 |
| `deps:`            | patch        | `deps: upgrade kotlinx-coroutines to 1.12.0`      |
| `docs:`            | none         | `docs: update README examples`                    |
| `chore:`           | none         | `chore: update CI action versions`                |

Use `!` after the type (or a `BREAKING CHANGE:` footer) for any backward-incompatible change.

## Local dev

Java 21 is required — do not downgrade the toolchain.

```bash
# Primary dev loop: run all JVM tests
./gradlew jvmTest

# Run a single test class
./gradlew jvmTest --tests "*.KeyValueStorageTest"

# Schema parity / migration gate (runs as part of jvmTest)
./gradlew jvmTest --tests "*.SchemaParityTest" --tests "*.SchemaMigrationTest"

# Run Android instrumented tests on a managed emulator (CI only, slow)
./gradlew allDevicesDebugAndroidTest

# Publish a snapshot to your local Maven repo for integration testing
./gradlew publishToMavenLocal
```

For the documentation site:

```bash
cd docs
bundle install
bundle exec jekyll serve --livereload
# → http://127.0.0.1:4000/sqkon/
```

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs on every push and PR to `main`:

- **`jvm-tests`** — runs `./gradlew jvmTest`. `SchemaParityTest` + `SchemaMigrationTest` are the schema regression gate.
- **`run-android-tests`** — runs `./gradlew allDevicesDebugAndroidTest` on a managed emulator.
- **`Compile iOS targets`** — compiles the iOS Kotlin/Native targets so the iOS scaffold keeps building.

All three jobs must pass before a PR can merge. JUnit reports are surfaced as GitHub check annotations.

## Releases

Releases are fully automated via [release-please](https://github.com/googleapis/release-please):

1. Merge PRs to `main` with conventional commits.
2. Release-please opens a PR titled `release: <version>` that accumulates changes and updates `CHANGELOG.md`.
3. Merging that release PR creates a GitHub Release. The deploy workflow then validates semver, runs `jvmTest`, and publishes to Maven Central.

**Do not create GitHub releases manually** — let release-please handle the entire flow.

## Project rules

A few constraints exist for technical reasons. Please don't change these without discussion.

- **Keep the SQLite driver synchronous.** Sqkon serializes work through its own coroutine dispatchers (one writer, a small reader pool); don't reach for an async driver — it doesn't play well with multithreaded hosts.
- **Keep the `-Xexpect-actual-classes` compiler flag.** It's required for the KMP `expect`/`actual` declarations Sqkon relies on.
- **Do not add Android unit tests** (`enableUnitTest = false`). Use JVM tests for fast iteration; Android instrumented tests cover device-specific behavior.
- **Java 21 toolchain is required.** Do not downgrade.

## Code of conduct

Be respectful. Disagree on technical merit, not on people. Reviews should focus on the change, not the contributor.
