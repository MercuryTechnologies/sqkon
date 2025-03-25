package com.mercury.sqkon.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildReceiverParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isSimpleProperty
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

/**
 * IR Transformer that processes serializable classes and adds property index information.
 */
class SerialIndexIrTransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector
) : IrElementTransformerVoidWithContext() {

    private val serializableAnnotationFqName = FqName("kotlinx.serialization.Serializable")
    private val transientAnnotationFqName = FqName("kotlinx.serialization.Transient")

    override fun visitClassNew(declaration: IrClass): IrStatement {
        // Process the class first with super (to process nested classes)
        val irClass = super.visitClassNew(declaration) as IrClass

        // Check if this class has @Serializable annotation
        val isSerializable = irClass.annotations.any {
            it.symbol.owner.parentAsClass.fqNameWhenAvailable == serializableAnnotationFqName
        }

        if (!isSerializable) return irClass

        messageCollector.report(
            CompilerMessageSeverity.INFO,
            "Processing serializable class: ${irClass.name}"
        )

        // Add or find companion object
        val companion = getOrCreateCompanionObject(irClass)

        // Add property indices map to companion
        addPropertyIndicesMap(companion, irClass)

        return irClass
    }

    /**
     * Finds existing companion object or creates a new one.
     */
    private fun getOrCreateCompanionObject(irClass: IrClass): IrClass {

        // Try to find existing companion object
        irClass.companionObject()?.let { return it }

        // Need to create a new companion object
        messageCollector.report(
            CompilerMessageSeverity.INFO,
            "Creating companion object for class ${irClass.name}"
        )

        // Create a new companion object class
        val companionObject = pluginContext.irFactory.buildClass {
            name = SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT
            kind = ClassKind.OBJECT
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
            isCompanion = true
        }.also { companionObject ->
            companionObject.createThisReceiverParameter()
            companionObject.addConstructor {
                visibility = DescriptorVisibilities.PRIVATE
                isPrimary = true
            }.also { constructor ->
                constructor.body = pluginContext.irFactory.createBlockBody(
                    startOffset = constructor.startOffset,
                    endOffset = constructor.endOffset,
                ) {
                    statements.add(
                        IrDelegatingConstructorCallImpl(
                            startOffset = constructor.startOffset,
                            endOffset = constructor.endOffset,
                            type = pluginContext.irBuiltIns.anyType,
                            symbol = pluginContext.irBuiltIns.anyClass.constructors.single(),
                            typeArgumentsCount = 0,
                            origin = IrStatementOrigin.DEFAULT_VALUE,
                        )
                    )
                    statements.add(
                        IrInstanceInitializerCallImpl(
                            startOffset = constructor.startOffset,
                            endOffset = constructor.endOffset,
                            classSymbol = companionObject.symbol,
                            type = pluginContext.irBuiltIns.unitType
                        )
                    )
                }
            }
        }

        // Add the companion object to the parent class
        // companionObject.parent = irClass
        irClass.addChild(companionObject)

        return companionObject
    }

    /**
     * Adds the propertyIndices map to the companion object.
     */
    private fun addPropertyIndicesMap(companion: IrClass, parentClass: IrClass) {
        // Get the list of properties from the parent class
        val properties = parentClass.declarations
            .filterIsInstance<IrProperty>()
            // TODO also filter @Transient properties
            .filter {
                if (it.annotations.hasAnnotation(transientAnnotationFqName)) return@filter false
                it.isSimpleProperty
                        && it.visibility != DescriptorVisibilities.PRIVATE
                        && !it.isDelegated
                        && !it.isLateinit
                        && it.getter != null
            }

        messageCollector.report(
            CompilerMessageSeverity.INFO,
            "Found ${properties.size} properties for ${parentClass.name}"
        )

        // Create a map of property name to index
        val propertyIndices = properties.mapIndexed { index, property ->
            property.name.asString() to index
        }.toMap()

        // Get the Map<String, Int> type
        val stringClassId = ClassId(FqName("kotlin"), Name.identifier("String"))
        val intClassId = ClassId(FqName("kotlin"), Name.identifier("Int"))
        val mapType = getMapType(
            pluginContext.referenceClass(stringClassId)!!.defaultType,
            pluginContext.referenceClass(intClassId)!!.defaultType
        )

        companion.addProperty {
            this.name = Name.identifier("propertyIndices")
            this.visibility = DescriptorVisibilities.PRIVATE
            this.modality = Modality.FINAL
            this.isVar = false
        }.also { property ->
            property.addGetter {
                visibility = DescriptorVisibilities.PUBLIC
                modality = Modality.FINAL
                returnType = mapType
            }.also { getter ->
                // Not sure if needed?
                getter.addValueParameter {
                    name = SpecialNames.THIS
                    type = companion.defaultType
                }
                // dunno if needed?
                getter.buildReceiverParameter {
                    type = companion.defaultType
                }
                getter.body = DeclarationIrBuilder(pluginContext, getter.symbol).irBlockBody {
                    +irReturn(
                        createMapOfCall(propertyIndices)
                    )
                }
            }
        }

        messageCollector.report(
            CompilerMessageSeverity.INFO,
            "Added propertyIndices map to ${parentClass.name} companion"
        )

    }

    /**
     * Creates a call to mapOf() with the given property indices.
     */
    private fun IrBuilderWithScope.createMapOfCall(propertyIndices: Map<String, Int>): IrExpression {
        // Find the mapOf function
        val mapOfFunction = findMapOfFunction()
        // Get types for String and Int
        val stringType = pluginContext.irBuiltIns.stringType
        val intType = pluginContext.irBuiltIns.intType

        // For mapOf, we need to provide the vararg of pairs
        val elementType = findPairType(stringType, intType)
        val arguments = irVararg(
            elementType = elementType,
            values = propertyIndices.map { (name, index) ->
                // Create a Pair using "to" infix function
                createPair(name, index)
            }
        )

        // Create the call to mapOf
        return irCall(mapOfFunction).apply {
            putTypeArgument(0, stringType) // Key type
            putTypeArgument(1, intType) // Value type
            // Put all pairs into a vararg
            putValueArgument(0, arguments)
        }
    }

    /**
     * Creates a Pair(name, index) using the "to" infix function.
     */
    private fun IrBuilderWithScope.createPair(name: String, index: Int): IrExpression {
        // Get types for String and Int
        val stringType = pluginContext.irBuiltIns.stringType
        val intType = pluginContext.irBuiltIns.intType

        // Find the "to" extension function
        val toFunction = findToFunction()

        // Create "name" to index
        return irCall(toFunction).apply {
            putTypeArgument(0, stringType)// A type
            putTypeArgument(1, intType)// B type
            insertExtensionReceiver(irString(name))
            putValueArgument(0, irInt(index))
        }
    }

    /**
     * Finds the mapOf function in the standard library.
     */
    private fun findMapOfFunction(): IrSimpleFunctionSymbol {
        // Look for the mapOf function with vararg parameter
        val mapOfCallable = CallableId(
            FqName("kotlin.collections"), Name.identifier("mapOf")
        )
        val mapOfFunctions = pluginContext.referenceFunctions(mapOfCallable)

        return mapOfFunctions.single { function ->
            function.owner.valueParameters.size == 1 && function.owner.valueParameters[0].isVararg
        }
    }

    /**
     * Finds the "to" infix function for creating pairs.
     */
    private fun findToFunction(): IrSimpleFunctionSymbol {
        // Find the "to" extension function
        val toCallable = CallableId(FqName("kotlin"), Name.identifier("to"))
        return pluginContext.referenceFunctions(toCallable).single { function ->
            function.owner.isInfix
                    && function.owner.valueParameters.size == 1
                    && function.owner.extensionReceiverParameter != null
        }
    }

    /**
     * Gets the Map<K, V> type.
     */
    private fun getMapType(keyType: IrType, valueType: IrType): IrType {
        val mapClassId = ClassId(FqName("kotlin.collections"), Name.identifier("Map"))
        val mapClass = pluginContext.referenceClass(mapClassId)!!
        return mapClass.typeWith(listOf(keyType, valueType))
    }

    /**
     * Finds the Pair<A, B> type.
     */
    private fun findPairType(aType: IrType, bType: IrType): IrType {
        val pairClassId = ClassId(FqName("kotlin"), Name.identifier("Pair"))
        val pairClass = pluginContext.referenceClass(pairClassId)!!
        return pairClass.typeWith(listOf(aType, bType))
    }

}
