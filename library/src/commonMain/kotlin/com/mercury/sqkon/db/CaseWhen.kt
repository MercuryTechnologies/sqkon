package com.mercury.sqkon.db

import kotlinx.serialization.serializer
import kotlin.reflect.KProperty1

/**
 * A SQL `CASE … WHEN … END` value expression. Build via [case] on a sealed parent property
 * or KClass. Use as the LHS of a comparison operator (`eq`, `gt`, …) to produce a [Where], or
 * pass to [OrderBy] to drive ordering.
 */
class CaseWhen<T : Any> internal constructor(
    private val discriminatorPath: String,
    private val branches: List<Branch>,
    private val elseValuePath: String?,
) {
    @PublishedApi
    internal data class Branch(val discriminatorValue: String, val valuePath: String)

    internal fun toSqlValue(): SqlValueFragment {
        // Minimal impl for Task 2: only single-branch, no ELSE. Generalized in Task 3.
        val branch = branches.single()
        require(elseValuePath == null) { "ELSE branch not yet supported" }
        return SqlValueFragment(
            sql = "(CASE WHEN json_extract(entity.value, ?) = ? THEN json_extract(entity.value, ?) END)",
            parameters = 3,
            bindArgs = {
                bindString(discriminatorPath)
                bindString(branch.discriminatorValue)
                bindString(branch.valuePath)
            },
        )
    }
}

internal data class SqlValueFragment(
    val sql: String,
    val parameters: Int,
    val bindArgs: AutoIncrementSqlPreparedStatement.() -> Unit,
)

class CaseWhenBuilder<T : Any> @PublishedApi internal constructor(
    @PublishedApi internal val discriminatorPath: String,
) {
    @PublishedApi internal val branches: MutableList<CaseWhen.Branch> = mutableListOf()
    @PublishedApi internal var elseValuePath: String? = null

    inline fun <reified V : Any> whenIs(valuePath: JsonPathBuilder<T>) {
        branches += CaseWhen.Branch(
            discriminatorValue = serializer<V>().descriptor.serialName,
            valuePath = valuePath.buildPath(),
        )
    }

    fun elseValue(valuePath: JsonPathBuilder<T>) {
        elseValuePath = valuePath.buildPath()
    }

    @PublishedApi internal fun build(): CaseWhen<T> =
        CaseWhen(discriminatorPath, branches.toList(), elseValuePath)
}

/**
 * `CASE WHEN` expression rooted at a sealed parent property (e.g. `MyEntity::status`).
 * The variant discriminator is read from `<parentPath>[0]`; payload paths sit under `[1]`.
 */
inline fun <reified T : Any, reified S : Any> KProperty1<T, S>.case(
    block: CaseWhenBuilder<T>.() -> Unit,
): CaseWhen<T> {
    val parentPath = this.builder<T, S>().buildPath()
    require(parentPath.endsWith("[1]")) {
        "case() must be called on a sealed property; got path $parentPath"
    }
    val discriminatorPath = parentPath.removeSuffix("[1]") + "[0]"
    return CaseWhenBuilder<T>(discriminatorPath).apply(block).build()
}
