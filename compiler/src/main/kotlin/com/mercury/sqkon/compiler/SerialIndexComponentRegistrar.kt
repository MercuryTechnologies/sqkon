package com.mercury.sqkon.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Entry point for our compiler plugin.
 * Registers the IR extension that will process serializable classes.
 */
@OptIn(ExperimentalCompilerApi::class)
class SerialIndexComponentRegistrar : CompilerPluginRegistrar() {

    override val supportsK2: Boolean get() = true
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messageCollector = configuration.getNotNull(
            CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY
        )
        IrGenerationExtension.registerExtension(
            SerialIndexIrGenerationExtension(messageCollector)
        )
    }

}
