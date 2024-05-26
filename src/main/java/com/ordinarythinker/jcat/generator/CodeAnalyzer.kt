package com.ordinarythinker.jcat.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.ordinarythinker.jcat.enums.InteractionType
import com.ordinarythinker.jcat.enums.KeyboardType
import com.ordinarythinker.jcat.enums.ParameterType
import com.ordinarythinker.jcat.enums.VisualTransformationType
import com.ordinarythinker.jcat.models.FunctionTest
import com.ordinarythinker.jcat.models.Parameter
import com.ordinarythinker.jcat.models.TestNode
import com.ordinarythinker.jcat.settings.Settings
import com.ordinarythinker.jcat.utils.*
import com.ordinarythinker.jcat.utils.Cons.clickables
import com.ordinarythinker.jcat.utils.Cons.textFields
import com.ordinarythinker.jcat.utils.Cons.visibles
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI

class CodeAnalyzer(
    private val project: Project,
    private val file: KtFile,
) {
    private val mocker = Mocker(project)
    private val settings: Settings = Settings.init(project)

    private val functions = mutableListOf<KtNamedFunction>()
    private val tests = mutableListOf<FunctionTest>()

    fun analyze(): List<FunctionTest> {
        findComposableDeclarations()

        functions.forEach { function ->
            val parameters = extractParameters(function)
            val mocks = mocker.generateMockData(parameters)
            val imports = mutableListOf<String>()
            imports.addAll(mocker.imports)
            val fqName = function.fqName?.asString()
            fqName?.let { imports.add(it) }


            val subFunctions = findComposableFunctionsInBody(function)

            val interactionsForScreen = mutableListOf<List<TestNode>>()
            subFunctions.forEach { uiComponent ->
                val result = defineScenarios(uiComponent)
                interactionsForScreen.addAll(result)
            }

            tests.add(
                FunctionTest(
                    function = function,
                    mocks = mocks,
                    imports = imports,
                    scenarios = interactionsForScreen
                )
            )
        }

        return tests
    }

    private fun findComposableDeclarations() {
        file.acceptChildren(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KtNamedFunction) {
                    val annotation = element.annotationEntries.find { it.isComposableAnnotation() }
                    if (annotation != null) {
                        functions.add(element)
                    }
                }
                super.visitElement(element)
            }
        })
    }

    private fun findComposableFunctionsInBody(function: KtNamedFunction): List<KtCallExpression> {
        val callExpressions = PsiTreeUtil.findChildrenOfType(function.bodyBlockExpression, KtCallExpression::class.java)
        return callExpressions.filter { func ->
            val calleeExpression = func.calleeExpression
            if (calleeExpression is KtNameReferenceExpression) {
                val functionName = calleeExpression.getReferencedName()
                println(functionName)
            }

            isComposableFunction(func)
        }
    }

    @OptIn(IDEAPluginsCompatibilityAPI::class)
    private fun isComposableFunction(callExpression: KtCallExpression): Boolean {
        return try {
            val bindingContext = callExpression.analyze(BodyResolveMode.FULL)
            val referenceExpression = callExpression.calleeExpression as? KtReferenceExpression
            val resolvedCall: ResolvedCall<out CallableDescriptor>? = referenceExpression.getResolvedCall(bindingContext)
            val resultingDescriptor = resolvedCall?.resultingDescriptor
            if (resultingDescriptor is FunctionDescriptor) {
                val annotations = resultingDescriptor.annotations
                annotations.any {
                    it.fqName?.asString()?.contains("Composable")
                        ?: false
                }
            } else false
        } catch (e: Exception) {
            false
        }
    }

    @OptIn(IDEAPluginsCompatibilityAPI::class)
    fun findFunctionDeclaration(callExpression: KtCallExpression): KtNamedFunction? {
        val bindingContext = callExpression.analyze(BodyResolveMode.FULL)
        val referenceExpression = callExpression.calleeExpression as? KtReferenceExpression ?: return null
        val resolvedCall: ResolvedCall<out CallableDescriptor>? = referenceExpression.getResolvedCall(bindingContext)
        val resultingDescriptor = resolvedCall?.resultingDescriptor ?: return null
        if (resultingDescriptor is FunctionDescriptor) {
            val declaration = resultingDescriptor.source.getPsi()
            if (declaration is KtNamedFunction) {
                return declaration
            }
        }
        return null
    }

    private fun defineScenarios(ktCallExpression: KtCallExpression): List<List<TestNode>> {
        val interactions = defineInteractionType(ktCallExpression)
        return getAllCombinations(interactions)
    }

    private fun defineInteractionType(ktCallExpression: KtCallExpression): List<List<TestNode>> {
        val calleeExpression = ktCallExpression.calleeExpression
        val name = if (calleeExpression is KtNameReferenceExpression) {
            val functionName = calleeExpression.getReferencedName()
            println(functionName)
            functionName
        } else null

        val testTag = retrieveTestTagFromComposable(ktCallExpression)
        val nodes = mutableListOf<List<TestNode>>()

        val tryRecursively = {
            val funDeclaration = findFunctionDeclaration(ktCallExpression)
            funDeclaration?.let { declaration ->
                val calls = findComposableFunctionsInBody(declaration)
                val interactions = mutableListOf<List<TestNode>>()
                calls.forEach { call ->
                    val interaction = defineInteractionType(call)
                    interactions.addAll(interaction)
                }
            }
        }

        if (name != null) {
            if (testTag != null) {
                if (!settings.excludeTags.contains(testTag)) {
                    when (name) {
                        in textFields -> {
                            val keyboardType = getKeyboardType(ktCallExpression)
                            val testNoInput = settings.forNode.firstOrNull { it.nodeTag == testTag }?.rules?.useEmptyStrings
                                ?: settings.globalRules.useEmptyStrings

                            val inputs = mutableListOf<TestNode>()
                            if (testNoInput) {
                                inputs.add(
                                    TestNode(
                                        testTag = testTag,
                                        rule = InteractionType.Input.NoInput
                                    )
                                )
                            }

                            when (keyboardType) {
                                KeyboardType.Number, KeyboardType.Phone, KeyboardType.Decimal, KeyboardType.NumberPassword -> {
                                    inputs.add(
                                        TestNode(
                                            testTag = testTag,
                                            rule = InteractionType.Input.NumberInput()
                                        )
                                    )
                                }
                                KeyboardType.Text, KeyboardType.Password -> {
                                    inputs.add(
                                        TestNode(
                                            testTag = testTag,
                                            rule = InteractionType.Input.RandomStringInput()
                                        )
                                    )
                                }
                                KeyboardType.Email -> {
                                    inputs.add(
                                        TestNode(
                                            testTag = testTag,
                                            rule = InteractionType.Input.RandomStringInput()
                                        )
                                    )

                                    inputs.add(
                                        TestNode(
                                            testTag = testTag,
                                            rule = InteractionType.Input.ValidEmailInput()
                                        )
                                    )
                                }
                            }

                            nodes.add(inputs)
                        }

                        in clickables -> {
                            val testNoClicks = settings.forNode.firstOrNull { it.nodeTag == testTag }?.rules?.applyClickIgnore
                                ?: settings.globalRules.applyClickIgnore

                            val clicks = mutableListOf<TestNode>()
                            if (testNoClicks) {
                                clicks.add(
                                    TestNode(
                                        testTag = testTag,
                                        rule = InteractionType.Clickable.NoClick
                                    )
                                )
                            }

                            clicks.add(
                                TestNode(
                                    testTag = testTag,
                                    rule = InteractionType.Clickable.PerformClick
                                )
                            )

                            nodes.add(clicks)
                        }

                        in visibles -> {
                            nodes.add(
                                listOf(
                                    TestNode(
                                        testTag = testTag,
                                        rule = InteractionType.Visibility
                                    )
                                )
                            )
                        }

                        else -> {
                            tryRecursively.invoke()
                        }
                    }
                }
            } else {
                tryRecursively.invoke()
            }
        }

        return nodes
    }

    private fun retrieveTestTagFromComposable(callExpression: KtCallExpression): String? {
        val valueArguments = callExpression.valueArguments
        for (arg in valueArguments) {
            val argExpression = arg.getArgumentExpression()
            if (argExpression != null && argExpression is KtDotQualifiedExpression) {
                val receiverExpression = argExpression.receiverExpression
                val selectorExpression = argExpression.selectorExpression
                if (receiverExpression.text == "Modifier" && selectorExpression is KtCallExpression) {
                    val testTagCallExpression = selectorExpression
                    if (testTagCallExpression.calleeExpression?.text == "testTag") {
                        val testTagValueArgument = testTagCallExpression.valueArguments.firstOrNull()
                        val testTagValue = testTagValueArgument?.getArgumentExpression()?.text
                        return testTagValue
                    }
                }
            }
        }
        return null
    }

    private fun getVisualTransformationType(element: PsiElement): VisualTransformationType? {
        when (element) {
            is KtNamedFunction -> {
                // Check function declaration parameters
                val visualTransformationParameter = element.valueParameters.find {
                    it.name == "visualTransformation"
                }
                visualTransformationParameter?.defaultValue?.text?.let { defaultValue ->
                    if (defaultValue.contains("PasswordVisualTransformation")) {
                        return VisualTransformationType.Password
                    }
                }
            }
            is KtCallExpression -> {
                // Check function call arguments
                val functionReference = element.reference
                val resolvedFunction = functionReference?.resolve() as? KtNamedFunction
                    ?: return null // Couldn't resolve function reference

                val visualTransformationParameter = resolvedFunction.valueParameters.find {
                    it.name == "visualTransformation"
                }
                visualTransformationParameter?.defaultValue?.text?.let { defaultValue ->
                    if (defaultValue.contains("PasswordVisualTransformation")) {
                        return VisualTransformationType.Password
                    }
                }
            }
        }
        return null
    }

    private fun getKeyboardType(callExpression: KtCallExpression): KeyboardType {
        val valueArguments = callExpression.valueArguments
        for (arg in valueArguments) {
            val argExpression = arg.getArgumentExpression()
            if (argExpression != null && argExpression is KtDotQualifiedExpression) {
                val receiverExpression = argExpression.receiverExpression
                val selectorExpression = argExpression.selectorExpression

                if (receiverExpression is KtDotQualifiedExpression && selectorExpression is KtCallExpression) {
                    val keyboardTypeArg = selectorExpression.valueArguments.firstOrNull {
                        val expr = it.getArgumentExpression()

                        expr is KtDotQualifiedExpression && expr.receiverExpression.text == "KeyboardType"
                    }

                    keyboardTypeArg?.let { typeArg ->
                        val valueExpression = typeArg.getArgumentExpression()

                        if (valueExpression != null && valueExpression is KtDotQualifiedExpression) {
                            val selector = valueExpression.selectorExpression?.text
                            return try {
                                selector?.let { s ->
                                    KeyboardType.valueOf(s)
                                } ?: KeyboardType.Text
                            } catch (e: Exception) {
                                KeyboardType.Text
                            }
                        }
                    }
                }
            }
        }
        return KeyboardType.Text
    }

    fun extractParameters(function: KtNamedFunction): List<Parameter> {
        val parameters = mutableListOf<Parameter>()

        function.valueParameters.forEach { parameter ->
            val paramName = parameter.name ?: return@forEach
            val paramTypeRef = parameter.typeReference ?: return@forEach
            val bindingContext = paramTypeRef.analyze(BodyResolveMode.PARTIAL)
            val kotlinType = bindingContext[BindingContext.TYPE, paramTypeRef] ?: return@forEach
            val paramClass = findClassByName(kotlinType)

            if (paramClass != null) {
                parameters.add(Parameter(name = paramName, type = paramClass))
            }
        }

        return parameters
    }

    private fun findClassByName(kotlinType: KotlinType): ParameterType? {
        val classDescriptor = kotlinType.constructor.declarationDescriptor as? ClassDescriptor
        val fqName = classDescriptor?.fqNameSafe

        return if (fqName != null) {

            if (fqName.asString().startsWith("kotlin.Function")) {
                return ParameterType.Function
            }

            val project = file.project
            val psiClass = KotlinFullClassNameIndex.getInstance()
                .get(fqName.asString(), project, GlobalSearchScope.allScope(project))
                .firstOrNull() as? KtClassOrObject
            if (psiClass != null) {
                ParameterType.Type(psiClass as KtClass)
            } else null
        } else null
    }
}
