package com.mercury.sqkon.db

import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Shared, dependency-free harness for the sqkon informational benchmark suite.
 *
 * Every benchmark class extends [BenchmarkSuite] and drives its cases through a [BenchmarkRunner],
 * which does `warmup` discarded iterations + `runs` measured iterations and writes one results file
 * per class under `build/benchmark-results/`. The suite is opt-in — each `@Test` starts with
 * [assumeBenchmarkEnabled], so it is skipped in the normal `jvmTest` gate and only runs when
 * `-Dsqkon.benchmark=true` is passed (see the `benchmark-suite` CI job).
 *
 * Deliberately dependency-free (kotlin.time + java.io.File + JUnit assumeTrue only). kotlinx-benchmark's
 * Gradle plugin bundles a Kotlin compiler plugin and does not state Kotlin 2.3 support (its latest
 * release targets Kotlin 2.2.0), so it is a build-breakage risk on this repo's Kotlin 2.3.21. Once
 * kotlinx-benchmark supports Kotlin 2.3, these can be promoted to real JMH benchmarks.
 *
 * Tunables (all optional JVM system properties, forwarded to the jvmTest JVM by build.gradle.kts):
 *   -Dsqkon.benchmark=true        enable the suite (required; skipped otherwise)
 *   -Dsqkon.benchmark.rows=N      dataset size (default 2000)
 *   -Dsqkon.benchmark.warmup=N    discarded warmup iterations per case (default 2)
 *   -Dsqkon.benchmark.runs=N      measured iterations per case (default 5)
 */
fun assumeBenchmarkEnabled() = assumeTrue(
    "set -Dsqkon.benchmark=true to run benchmarks",
    System.getProperty("sqkon.benchmark") == "true",
)

fun benchmarkRows(): Int = System.getProperty("sqkon.benchmark.rows")?.toIntOrNull() ?: 2000

fun benchmarkWarmup(): Int = System.getProperty("sqkon.benchmark.warmup")?.toIntOrNull() ?: 2

fun benchmarkRuns(): Int = System.getProperty("sqkon.benchmark.runs")?.toIntOrNull() ?: 5

/** One measured case: [total] is the summed wall time across [runs] iterations. */
data class BenchResult(
    val label: String,
    val runs: Int,
    val opsPerIter: Int,
    val total: Duration,
) {
    val perIter: Duration get() = total / runs
    val totalOps: Long get() = runs.toLong() * opsPerIter
    val opsPerSec: Double
        get() = if (total.inWholeNanoseconds == 0L) 0.0
        else totalOps / total.toDouble(DurationUnit.SECONDS)
}

/**
 * Runs benchmark cases and accumulates their results. [opsPerIter] is the number of logical
 * operations in a single `block()` call, so `ops_per_sec` is comparable across cases regardless of
 * how much each case does per iteration. [BenchmarkRunner.bench]'s optional `reset` runs untimed
 * before every iteration (warmup and measured) — use it for mutating cases that must start each
 * iteration from a known state.
 */
class BenchmarkRunner(
    private val suite: String,
    private val warmup: Int = benchmarkWarmup(),
    private val runs: Int = benchmarkRuns(),
) {
    private val results = mutableListOf<BenchResult>()

    suspend fun bench(
        label: String,
        opsPerIter: Int,
        reset: (suspend () -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        repeat(warmup) { reset?.invoke(); block() }
        var total = Duration.ZERO
        repeat(runs) {
            reset?.invoke()
            total += measureTime { block() }
        }
        results += BenchResult(label, runs, opsPerIter, total)
    }

    /** Writes `build/benchmark-results/<suite>.txt` and echoes it to stdout. Call once at the end. */
    fun report(meta: Map<String, Any> = emptyMap()) {
        val text = renderReport(suite, results, meta)
        println(text)
        File("build/benchmark-results").apply { mkdirs() }
            .resolve("$suite.txt").writeText(text)
    }
}

/** One `case=… per_iter_ms=… ops_per_sec=…` line per result — human-scannable and greppable. */
fun renderReport(suite: String, results: List<BenchResult>, meta: Map<String, Any>): String =
    buildString {
        appendLine("sqkon benchmark: $suite")
        if (meta.isNotEmpty()) appendLine(meta.entries.joinToString(" ") { "${it.key}=${it.value}" })
        results.forEach { r ->
            val perIterMs = r.perIter.inWholeMicroseconds / 1000.0
            val totalMs = r.total.inWholeMicroseconds / 1000.0
            appendLine(
                "case=${r.label} runs=${r.runs} ops_per_iter=${r.opsPerIter} " +
                    "per_iter_ms=$perIterMs total_ms=$totalMs ops_per_sec=${r.opsPerSec.toLong()}"
            )
        }
    }
