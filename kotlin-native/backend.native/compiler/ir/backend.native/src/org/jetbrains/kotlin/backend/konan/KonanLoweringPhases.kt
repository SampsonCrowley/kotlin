package org.jetbrains.kotlin.backend.konan

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.*
import org.jetbrains.kotlin.backend.common.lower.inline.FunctionInlining
import org.jetbrains.kotlin.backend.common.lower.inline.LocalClassesExtractionFromInlineFunctionsLowering
import org.jetbrains.kotlin.backend.common.lower.inline.LocalClassesInInlineFunctionsLowering
import org.jetbrains.kotlin.backend.common.lower.inline.LocalClassesInInlineLambdasLowering
import org.jetbrains.kotlin.backend.common.lower.loops.ForLoopsLowering
import org.jetbrains.kotlin.backend.common.lower.optimizations.FoldConstantLowering
import org.jetbrains.kotlin.backend.common.lower.optimizations.PropertyAccessorInlineLowering
import org.jetbrains.kotlin.backend.common.phaser.*
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.backend.konan.ir.FunctionsWithoutBoundCheckGenerator
import org.jetbrains.kotlin.backend.konan.lower.*
import org.jetbrains.kotlin.backend.konan.lower.InitializersLowering
import org.jetbrains.kotlin.backend.konan.optimizations.KonanBCEForLoopBodyTransformer
import org.jetbrains.kotlin.backend.konan.phases.BitcodegenContext
import org.jetbrains.kotlin.backend.konan.phases.MiddleEndContext
import org.jetbrains.kotlin.backend.konan.phases.PhaseContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal val validateAll = false

internal val filePhaseActions = setOfNotNull(
        defaultDumper,
        ::fileValidationCallback.takeIf { validateAll }
)
internal val modulePhaseActions: Set<(ActionState, IrModuleFragment, PhaseContext) -> Unit> = setOfNotNull(
        defaultDumper,
        // TODO: It is not valid
//        ::llvmIrDumpCallback,
        ::moduleValidationCallback.takeIf { validateAll }
)

internal fun makeKonanFileLoweringPhase(
        lowering: (MiddleEndContext) -> FileLoweringPass,
        name: String,
        description: String,
        prerequisite: Set<NamedCompilerPhase<MiddleEndContext, *>> = emptySet()
) = makeIrFilePhase(lowering, name, description, prerequisite, actions = filePhaseActions)

internal fun <Context : PhaseContext> makeKonanModuleLoweringPhase(
        lowering: (Context) -> FileLoweringPass,
        name: String,
        description: String,
        prerequisite: Set<NamedCompilerPhase<Context, *>> = emptySet()
) = makeIrModulePhase(lowering, name, description, prerequisite, actions = modulePhaseActions)

internal fun makeKonanFileOpPhase(
        op: (MiddleEndContext, IrFile) -> Unit,
        name: String,
        description: String,
        prerequisite: Set<NamedCompilerPhase<MiddleEndContext, *>> = emptySet()
) = NamedCompilerPhase(
        name, description, prerequisite, nlevels = 0,
        lower = object : SameTypeCompilerPhase<MiddleEndContext, IrFile> {
            override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState<IrFile>, context: MiddleEndContext, input: IrFile): IrFile {
                op(context, input)
                return input
            }
        },
        actions = filePhaseActions
)

internal fun <Context : KonanBackendContext> makeKonanModuleOpPhase(
        op: (Context, IrModuleFragment) -> Unit,
        name: String,
        description: String,
        prerequisite: Set<NamedCompilerPhase<Context, *>> = emptySet()
) = NamedCompilerPhase<Context, IrModuleFragment>(
        name, description, prerequisite, nlevels = 0,
        lower = object : SameTypeCompilerPhase<Context, IrModuleFragment> {
            override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState<IrModuleFragment>, context: Context, input: IrModuleFragment): IrModuleFragment {
                op(context, input)
                return input
            }
        },
//        actions = modulePhaseActions
)

internal val specialBackendChecksPhase = konanUnitPhase<MiddleEndContext>(
        op = { irModule!!.files.forEach { SpecialBackendChecksTraversal(this).lower(it) } },
        name = "SpecialBackendChecks",
        description = "Special backend checks"
)

internal val propertyAccessorInlinePhase = makeKonanModuleLoweringPhase<PhaseContext>(
        ::PropertyAccessorInlineLowering,
        name = "PropertyAccessorInline",
        description = "Property accessor inline lowering"
)

/* IrFile phases */

internal val removeExpectDeclarationsPhase = makeKonanFileLoweringPhase(
        ::ExpectDeclarationsRemoving,
        name = "RemoveExpectDeclarations",
        description = "Expect declarations removing"
)

internal val stripTypeAliasDeclarationsPhase = makeKonanFileLoweringPhase(
        { StripTypeAliasDeclarationsLowering() },
        name = "StripTypeAliasDeclarations",
        description = "Strip typealias declarations"
)

internal val annotationImplementationPhase = makeKonanFileLoweringPhase(
        { context -> AnnotationImplementationLowering { NativeAnnotationImplementationTransformer(context, it) } },
        name = "AnnotationImplementation",
        description = "Create synthetic annotations implementations and use them in annotations constructor calls"
)

internal val lowerBeforeInlinePhase = makeKonanFileLoweringPhase(
        ::PreInlineLowering,
        name = "LowerBeforeInline",
        description = "Special operations processing before inlining"
)

internal val arrayConstructorPhase = makeKonanFileLoweringPhase(
        ::ArrayConstructorLowering,
        name = "ArrayConstructor",
        description = "Transform `Array(size) { index -> value }` into a loop"
)

internal val lateinitPhase = makeKonanFileOpPhase(
        { context, irFile ->
            NullableFieldsForLateinitCreationLowering(context).lower(irFile)
            NullableFieldsDeclarationLowering(context).lower(irFile)
            LateinitUsageLowering(context).lower(irFile)
        },
        name = "Lateinit",
        description = "Lateinit properties lowering"
)

internal val sharedVariablesPhase = makeKonanFileLoweringPhase(
        ::SharedVariablesLowering,
        name = "SharedVariables",
        description = "Shared variable lowering",
//        prerequisite = setOf(lateinitPhase)
)

internal val inventNamesForLocalClasses = makeKonanFileLoweringPhase(
        { NativeInventNamesForLocalClasses(it) },
        name = "InventNamesForLocalClasses",
        description = "Invent names for local classes and anonymous objects"
)

internal val extractLocalClassesFromInlineBodies = makeKonanFileOpPhase(
        { context, irFile ->
            LocalClassesInInlineLambdasLowering(context).lower(irFile)
            LocalClassesInInlineFunctionsLowering(context).lower(irFile)
            LocalClassesExtractionFromInlineFunctionsLowering(context).lower(irFile)
        },
        name = "ExtractLocalClassesFromInlineBodies",
        description = "Extraction of local classes from inline bodies",
//        prerequisite = setOf(sharedVariablesPhase), // TODO: add "soft" dependency on inventNamesForLocalClasses
)

internal val wrapInlineDeclarationsWithReifiedTypeParametersLowering = makeKonanFileLoweringPhase(
        ::WrapInlineDeclarationsWithReifiedTypeParametersLowering,
        name = "WrapInlineDeclarationsWithReifiedTypeParameters",
        description = "Wrap inline declarations with reified type parameters"
)

internal val inlinePhase = makeKonanFileOpPhase(
        { context, irFile ->
            irFile.acceptChildrenVoid(object : IrElementVisitorVoid {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitFunction(declaration: IrFunction) {
                    if (declaration.isInline)
                        context.inlineFunctionsSupport.getNonLoweredInlineFunction(declaration)
                    declaration.acceptChildrenVoid(this)
                }
            })

            FunctionInlining(context, NativeInlineFunctionResolver(context)).lower(irFile)
        },
        name = "Inline",
        description = "Functions inlining",
//        prerequisite = setOf(lowerBeforeInlinePhase, arrayConstructorPhase, extractLocalClassesFromInlineBodies)
)

internal val postInlinePhase = makeKonanFileLoweringPhase(
        { PostInlineLowering(it) },
        name = "PostInline",
        description = "Post-processing after inlining"
)

internal val contractsDslRemovePhase = makeKonanFileLoweringPhase(
        { ContractsDslRemover(it) },
        name = "RemoveContractsDsl",
        description = "Contracts dsl removing"
)

// TODO make all lambda-related stuff work with IrFunctionExpression and drop this phase (see kotlin: dd3f8ecaacd)
internal val provisionalFunctionExpressionPhase = makeKonanFileLoweringPhase(
        { ProvisionalFunctionExpressionLowering() },
        name = "FunctionExpression",
        description = "Transform IrFunctionExpression to a local function reference"
)

internal val flattenStringConcatenationPhase = makeKonanFileLoweringPhase(
        ::FlattenStringConcatenationLowering,
        name = "FlattenStringConcatenationLowering",
        description = "Flatten nested string concatenation expressions into a single IrStringConcatenation"
)

internal val stringConcatenationPhase = makeKonanFileLoweringPhase(
        ::StringConcatenationLowering,
        name = "StringConcatenation",
        description = "String concatenation lowering"
)

internal val stringConcatenationTypeNarrowingPhase = makeKonanFileLoweringPhase(
        ::StringConcatenationTypeNarrowing,
        name = "StringConcatenationTypeNarrowing",
        description = "String concatenation type narrowing",
//        prerequisite = setOf(stringConcatenationPhase)
)

internal val kotlinNothingValueExceptionPhase = makeKonanFileLoweringPhase(
        ::KotlinNothingValueExceptionLowering,
        name = "KotlinNothingValueException",
        description = "Throw proper exception for calls returning value of type 'kotlin.Nothing'"
)

internal val enumConstructorsPhase = makeKonanFileLoweringPhase(
        ::EnumConstructorsLowering,
        name = "EnumConstructors",
        description = "Enum constructors lowering"
)

internal val initializersPhase = makeKonanFileLoweringPhase(
        ::InitializersLowering,
        name = "Initializers",
        description = "Initializers lowering",
//        prerequisite = setOf(enumConstructorsPhase)
)

internal val localFunctionsPhase = makeKonanFileOpPhase(
        op = { context, irFile ->
            LocalDelegatedPropertiesLowering().lower(irFile)
            LocalDeclarationsLowering(context).lower(irFile)
            LocalClassPopupLowering(context).lower(irFile)
        },
        name = "LocalFunctions",
        description = "Local function lowering",
        prerequisite = setOf(sharedVariablesPhase) // TODO: add "soft" dependency on inventNamesForLocalClasses
)

internal val tailrecPhase = makeKonanFileLoweringPhase(
        ::TailrecLowering,
        name = "Tailrec",
        description = "Tailrec lowering",
//        prerequisite = setOf(localFunctionsPhase)
)

internal val defaultParameterExtentPhase = makeKonanFileOpPhase(
        { context, irFile ->
            KonanDefaultArgumentStubGenerator(context).lower(irFile)
            DefaultParameterCleaner(context, replaceDefaultValuesWithStubs = true).lower(irFile)
            KonanDefaultParameterInjector(context).lower(irFile)
        },
        name = "DefaultParameterExtent",
        description = "Default parameter extent lowering",
//        prerequisite = setOf(tailrecPhase, enumConstructorsPhase)
)

internal val innerClassPhase = makeKonanFileLoweringPhase(
        ::InnerClassLowering,
        name = "InnerClasses",
        description = "Inner classes lowering",
//        prerequisite = setOf(defaultParameterExtentPhase)
)

internal val rangeContainsLoweringPhase = makeKonanFileLoweringPhase(
        ::RangeContainsLowering,
        name = "RangeContains",
        description = "Optimizes calls to contains() for ClosedRanges"
)

internal val functionsWithoutBoundCheck = makeKonanModuleOpPhase<MiddleEndContext>(
        name = "FunctionsWithoutBoundCheckGenerator",
        description = "Functions without bounds check generation",
        op = { context, _ ->
            FunctionsWithoutBoundCheckGenerator(context).generate()
        }
)

internal val forLoopsPhase = makeKonanFileOpPhase(
        { context, irFile ->
            ForLoopsLowering(context, KonanBCEForLoopBodyTransformer()).lower(irFile)
        },
        name = "ForLoops",
        description = "For loops lowering",
//        prerequisite = setOf(functionsWithoutBoundCheck)
)

internal val dataClassesPhase = makeKonanFileLoweringPhase(
        ::DataClassOperatorsLowering,
        name = "DataClasses",
        description = "Data classes lowering"
)

internal val finallyBlocksPhase = makeKonanFileOpPhase(
        { context, irFile -> FinallyBlocksLowering(context, context.irBuiltIns.throwableType).lower(irFile) },
        name = "FinallyBlocks",
        description = "Finally blocks lowering",
        prerequisite = setOf(initializersPhase, localFunctionsPhase, tailrecPhase)
)

internal val testProcessorPhase = makeKonanFileOpPhase(
        { context, irFile -> TestProcessor(context).process(irFile) },
        name = "TestProcessor",
        description = "Unit test processor"
)

internal val delegationPhase = makeKonanFileLoweringPhase(
        ::PropertyDelegationLowering,
        name = "Delegation",
        description = "Delegation lowering"
)

internal val functionReferencePhase = makeKonanFileLoweringPhase(
        ::FunctionReferenceLowering,
        name = "FunctionReference",
        description = "Function references lowering",
//        prerequisite = setOf(delegationPhase, localFunctionsPhase) // TODO: make weak dependency on `testProcessorPhase`
)

internal val enumWhenPhase = makeKonanFileLoweringPhase(
        ::NativeEnumWhenLowering,
        name = "EnumWhen",
        description = "Enum when lowering",
//        prerequisite = setOf(enumConstructorsPhase, functionReferencePhase)
)

internal val enumClassPhase = makeKonanFileLoweringPhase(
        ::EnumClassLowering,
        name = "Enums",
        description = "Enum classes lowering",
//        prerequisite = setOf(enumConstructorsPhase, functionReferencePhase, enumWhenPhase) // TODO: make weak dependency on `testProcessorPhase`
)

internal val enumUsagePhase = makeKonanFileLoweringPhase(
        ::EnumUsageLowering,
        name = "EnumUsage",
        description = "Enum usage lowering",
//        prerequisite = setOf(enumConstructorsPhase, functionReferencePhase, enumClassPhase)
)


internal val singleAbstractMethodPhase = makeKonanFileLoweringPhase(
        ::NativeSingleAbstractMethodLowering,
        name = "SingleAbstractMethod",
        description = "Replace SAM conversions with instances of interface-implementing classes",
//        prerequisite = setOf(functionReferencePhase)
)

internal val builtinOperatorPhase = makeKonanFileLoweringPhase(
        ::BuiltinOperatorLowering,
        name = "BuiltinOperators",
        description = "BuiltIn operators lowering",
//        prerequisite = setOf(defaultParameterExtentPhase, singleAbstractMethodPhase, enumWhenPhase)
)

internal val interopPhase = makeKonanFileLoweringPhase(
        ::InteropLowering,
        name = "Interop",
        description = "Interop lowering",
//        prerequisite = setOf(inlinePhase, localFunctionsPhase, functionReferencePhase)
)

internal val varargPhase = makeKonanFileLoweringPhase(
        ::VarargInjectionLowering,
        name = "Vararg",
        description = "Vararg lowering",
//        prerequisite = setOf(functionReferencePhase, defaultParameterExtentPhase, interopPhase, functionsWithoutBoundCheck)
)

internal val coroutinesPhase = makeKonanFileLoweringPhase(
        ::NativeSuspendFunctionsLowering,
        name = "Coroutines",
        description = "Coroutines lowering",
//        prerequisite = setOf(localFunctionsPhase, finallyBlocksPhase, kotlinNothingValueExceptionPhase)
)

internal val typeOperatorPhase = makeKonanFileLoweringPhase(
        ::TypeOperatorLowering,
        name = "TypeOperators",
        description = "Type operators lowering",
//        prerequisite = setOf(coroutinesPhase)
)

internal val bridgesPhase = makeKonanFileOpPhase(
        { context, irFile ->
            BridgesBuilding(context).runOnFilePostfix(irFile)
            WorkersBridgesBuilding(context).lower(irFile)
        },
        name = "Bridges",
        description = "Bridges building",
//        prerequisite = setOf(coroutinesPhase)
)

internal val autoboxPhase = makeKonanFileLoweringPhase(
        ::Autoboxing,
        name = "Autobox",
        description = "Autoboxing of primitive types",
//        prerequisite = setOf(bridgesPhase, coroutinesPhase)
)

internal val unboxInlinePhase = makeKonanModuleLoweringPhase<BitcodegenContext>(
        ::UnboxInlineLowering,
        name = "UnboxInline",
        description = "Unbox functions inline lowering",
        prerequisite = setOf()//setOf(autoboxPhase, redundantCoercionsCleaningPhase) // Different context types
)

internal val expressionBodyTransformPhase = makeKonanFileLoweringPhase(
        ::ExpressionBodyTransformer,
        name = "ExpressionBodyTransformer",
        description = "Replace IrExpressionBody with IrBlockBody"
)

internal val constantInliningPhase = makeKonanFileLoweringPhase(
        ::ConstLowering,
        name = "ConstantInlining",
        description = "Inline const fields reads",
)

internal val fileInitializersPhase = makeKonanFileLoweringPhase(
        ::FileInitializersLowering,
        name = "FileInitializers",
        description = "Add calls to file initializers",
//        prerequisite = setOf(expressionBodyTransformPhase)
)

internal val ifNullExpressionsFusionPhase = makeKonanFileLoweringPhase(
        ::IfNullExpressionsFusionLowering,
        name = "IfNullExpressionsFusionLowering",
        description = "Simplify '?.' and '?:' operator chains"
)

internal val foldConstantLoweringPhase = makeKonanFileOpPhase(
        { context, irFile -> FoldConstantLowering(context).lower(irFile) },
        name = "FoldConstantLowering",
        description = "Constant Folding",
//        prerequisite = setOf(flattenStringConcatenationPhase)
)

internal val computeStringTrimPhase = makeKonanFileLoweringPhase(
        ::StringTrimLowering,
        name = "StringTrimLowering",
        description = "Compute trimIndent and trimMargin operations on constant strings"
)

internal val exportInternalAbiPhase = makeKonanFileLoweringPhase(
        ::ExportCachesAbiVisitor,
        name = "ExportInternalAbi",
        description = "Add accessors to private entities"
)

internal val useInternalAbiPhase = makeKonanFileLoweringPhase(
        ::ImportCachesAbiTransformer,
        name = "UseInternalAbi",
        description = "Use internal ABI functions to access private entities"
)