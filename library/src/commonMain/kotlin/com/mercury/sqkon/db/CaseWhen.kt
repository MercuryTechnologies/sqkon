package com.mercury.sqkon.db

import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * A SQL `CASE … WHEN … END` value expression. Build via [case] on a sealed parent property
 * or KClass. Use as the LHS of a comparison operator (`eq`, `gt`, …) to produce a [Where], or
 * pass to [OrderBy] to drive ordering.
 */
data class CaseWhen<T : Any> internal constructor(
    private val discriminatorPath: String,
    private val branches: List<Branch>,
    private val elseValuePath: String?,
) {
    @PublishedApi
    internal data class Branch(val discriminatorValue: String, val valuePath: String)

    private val fragment: SqlValueFragment by lazy {
        require(branches.isNotEmpty()) { "CaseWhen requires at least one whenIs branch" }
        val whenSql = "WHEN json_extract(entity.value, ?) = ? THEN json_extract(entity.value, ?)"
        val elseSql = elseValuePath?.let { " ELSE json_extract(entity.value, ?)" } ?: ""
        SqlValueFragment(
            sql = "(CASE ${branches.joinToString(" ") { whenSql }}$elseSql END)",
            parameters = branches.size * 3 + (if (elseValuePath != null) 1 else 0),
            bindArgs = {
                branches.forEach { branch ->
                    bindString(discriminatorPath)
                    bindString(branch.discriminatorValue)
                    bindString(branch.valuePath)
                }
                elseValuePath?.let { bindString(it) }
            },
        )
    }

    internal fun toSqlValue(): SqlValueFragment = fragment
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

/**
 * `CASE WHEN` expression rooted at the entity itself when it is a sealed type
 * (e.g. `KeyValueStorage<BaseSealed>`). Discriminator path is `$[0]`, payload is under `$[1]`.
 *
 * The receiver provides the type anchor `R` for inference; its identity is unused at runtime.
 */
@Suppress("UnusedReceiverParameter")
inline fun <reified R : Any> KClass<R>.case(
    block: CaseWhenBuilder<R>.() -> Unit,
): CaseWhen<R> {
    return CaseWhenBuilder<R>(discriminatorPath = "\$[0]").apply(block).build()
}

