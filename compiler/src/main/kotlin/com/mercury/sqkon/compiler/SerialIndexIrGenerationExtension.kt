package com.mercury.sqkon.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * IR generation extension that processes the module fragment.
 */
class SerialIndexIrGenerationExtension(
    private val messageCollector: MessageCollector
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // Transform the IR
        val transformer = SerialIndexIrTransformer(pluginContext, messageCollector)
        moduleFragment.transform(transformer, null)
    }
}
