package com.mercury.sqkon.db

import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * A SQL `CASE WHEN <disc> = ? THEN <pred> [...] [ELSE <pred>] END` expression
 * usable as a top-level [Where].
 *
 * Each branch holds its own [Where] predicate. At lowering time, branch
 * predicates emit *scalar* SQL via [Where.toScalarSqlValue] so they compose
 * inside the CASE without LATERAL `json_tree` joins.
 *
 * Variants/values not listed are excluded — unmatched rows fall through to
 * SQL NULL (falsy in WHERE). Use `default { ... }` for an explicit ELSE.
 */
class CaseWhere<T : Any> internal constructor(
    private val discriminatorPath: String,
    private val branches: List<Branch<T>>,
    private val defaultPredicate: Where<T>?,
) : Where<T>() {

    @PublishedApi
    internal data class Branch<T : Any>(
        val discriminatorValue: String,
        val predicate: Where<T>,
    )

    private fun buildFragment(): SqlValueFragment {
        require(branches.isNotEmpty() || defaultPredicate != null) {
            "caseWhere requires at least one whenIs/whenEq branch or a default { }"
        }
        val parts = mutableListOf<String>()
        var paramCount = 0
        val binders = mutableListOf<AutoIncrementSqlPreparedStatement.() -> Unit>()

        for (branch in branches) {
            val pred = branch.predicate.toScalarSqlValue()
            parts += "WHEN json_extract(entity.value, ?) = ? THEN ${pred.sql}"
            paramCount += 2 + pred.parameters
            val capturedDiscValue = branch.discriminatorValue
            val capturedPred = pred
            binders += {
                bindString(discriminatorPath)
                bindString(capturedDiscValue)
                capturedPred.bindArgs(this)
            }
        }
        defaultPredicate?.let {
            val pred = it.toScalarSqlValue()
            parts += "ELSE ${pred.sql}"
            paramCount += pred.parameters
            binders += { pred.bindArgs(this) }
        }

        return SqlValueFragment(
            sql = "(CASE ${parts.joinToString(" ")} END)",
            parameters = paramCount,
            bindArgs = { binders.forEach { it(this) } },
        )
    }

    override fun toSqlQuery(increment: Int): SqlQuery {
        val frag = buildFragment()
        return SqlQuery(
            from = null,
            where = frag.sql,
            parameters = frag.parameters,
            bindArgs = frag.bindArgs,
        )
    }

    override fun toScalarSqlValue(): SqlValueFragment = buildFragment()
}

/**
 * Typed scope inside a `whenIs<V> { ... }` branch.
 *
 * Exposes a narrowed `with(KProperty1<V, X>)` (via the [with] extension below)
 * so users can only reach into the variant's own properties —
 * `with(OtherVariant::field)` won't compile.
 */
class CaseWhereBranch<T : Any, V : T> @PublishedApi internal constructor(
    @PublishedApi internal val payloadPath: String,
)

/**
 * Reach into a property of the variant `V` from inside a `whenIs<V> { ... }`
 * scope, producing a [JsonPathBuilder] rooted at the variant payload (e.g.
 * `$[1].dueAt` for a sealed-root entity).
 */
inline fun <T : Any, reified V : T, reified X> CaseWhereBranch<T, V>.with(
    prop: KProperty1<V, X>,
): JsonPathBuilder<T> {
    val propPath = prop.builder().buildPath().removePrefix("\$")
    val builder = JsonPathBuilder<T>()
    builder.rawPath = payloadPath + propPath
    return builder
}

/**
 * Nested-path variant — chains `then` onto the variant property path.
 */
inline fun <T : Any, reified V : T, reified X> CaseWhereBranch<T, V>.with(
    prop: KProperty1<V, X>,
    nested: JsonPathBuilder<V>.() -> JsonPathBuilder<V>,
): JsonPathBuilder<T> {
    val nestedPath = prop.builder<V, X>().nested().buildPath().removePrefix("\$")
    val builder = JsonPathBuilder<T>()
    builder.rawPath = payloadPath + nestedPath
    return builder
}

class CaseWhereBuilder<T : Any> @PublishedApi internal constructor(
    @PublishedApi internal val discriminatorPath: String,
    @PublishedApi internal val payloadPath: String,
) {
    @PublishedApi internal val branches: MutableList<CaseWhere.Branch<T>> = mutableListOf()
    @PublishedApi internal var default: Where<T>? = null
    @PublishedApi internal var defaultSet: Boolean = false

    inline fun <reified V : T> whenIs(block: CaseWhereBranch<T, V>.() -> Where<T>) {
        val pred = CaseWhereBranch<T, V>(payloadPath).block()
        branches += CaseWhere.Branch(
            discriminatorValue = serializer<V>().descriptor.serialName,
            predicate = pred,
        )
    }

    fun default(block: () -> Where<T>) {
        require(!defaultSet) { "default { ... } may only be specified once" }
        defaultSet = true
        default = block()
    }

    @PublishedApi internal fun build(): CaseWhere<T> =
        CaseWhere(discriminatorPath, branches.toList(), default)
}

/**
 * Predicate-dispatch CASE/WHEN rooted at the entity itself when it is a
 * sealed type. Discriminator at `$[0]`, payload under `$[1]`.
 *
 * The receiver provides the type anchor `R` for inference; its identity is
 * unused at runtime.
 */
@Suppress("UnusedReceiverParameter")
inline fun <reified R : Any> KClass<R>.caseWhere(
    block: CaseWhereBuilder<R>.() -> Unit,
): Where<R> = CaseWhereBuilder<R>(
    discriminatorPath = "\$[0]",
    payloadPath = "\$[1]",
).apply(block).build()
