package com.ordinarythinker.jcat.generator

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.ordinarythinker.jcat.enums.Interaction
import com.ordinarythinker.jcat.enums.InteractionType
import com.ordinarythinker.jcat.enums.KeyboardType
import com.ordinarythinker.jcat.enums.VisualTransformationType
import com.ordinarythinker.jcat.models.FunctionTest
import com.ordinarythinker.jcat.models.Parameter
import com.ordinarythinker.jcat.models.TestNode
import com.ordinarythinker.jcat.settings.Settings
import com.ordinarythinker.jcat.utils.*
import com.ordinarythinker.jcat.utils.Cons.clickables
import com.ordinarythinker.jcat.utils.Cons.textFields
import com.ordinarythinker.jcat.utils.Cons.visibles
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType

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
            val imports = mocker.imports
            val subFunctions = findComposableFunctionsInBody(function)

            val interactionsForScreen = mutableListOf<List<TestNode>>()
            subFunctions.forEach { uiComponent ->
                val result = defineScenarios(uiComponent)
                interactionsForScreen.addAll(result)
            }

            tests.add(
                FunctionTest(
                    function = function,
                    parameters = parameters.map { it.name },
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
        return callExpressions.filter { isComposableFunction(it) }
    }

    private fun isComposableFunction(callExpression: KtCallExpression): Boolean {
        val resolvedFunction = try {
            callExpression.resolve() as? KtNamedFunction?
        } catch (e: Exception) {
            null
        }
        val annotations = resolvedFunction?.annotationEntries
        val isComposable = annotations?.any { it.isComposableAnnotation() }
        return isComposable ?: false
    }

    private fun defineScenarios(ktCallExpression: KtCallExpression): List<List<TestNode>> {
        val interactions = defineInteractionType(ktCallExpression)
        return generateAllCombinations(interactions)
    }

    private fun defineInteractionType(ktCallExpression: KtCallExpression): List<Interaction> {
        val name = ktCallExpression.name
        val testTag = retrieveTestTagFromComposable(ktCallExpression)
        val nodes = mutableListOf<Interaction>()

        if (name != null && testTag != null) {
            if (!settings.excludeTags.contains(testTag)) {
                when (name) {
                    in textFields -> {
                        val keyboardType = getKeyboardType(ktCallExpression)
                        val testNoInput = settings.forNode.firstOrNull { it.nodeTag == testTag }?.rules?.useEmptyStrings
                            ?: settings.globalRules.useEmptyStrings

                        val inputs = mutableListOf<InteractionType.Input>()
                        if (testNoInput) {
                            inputs.add(InteractionType.Input.NoInput)
                        }

                        when (keyboardType) {
                            KeyboardType.Number, KeyboardType.Phone, KeyboardType.Decimal, KeyboardType.NumberPassword -> {
                                inputs.add(InteractionType.Input.NumberInput())
                            }
                            KeyboardType.Text, KeyboardType.Password -> {
                                inputs.add(InteractionType.Input.RandomStringInput())
                            }
                            KeyboardType.Email -> {
                                inputs.add(InteractionType.Input.RandomStringInput())
                                inputs.add(InteractionType.Input.ValidEmailInput())
                            }
                        }

                        nodes.add(
                            Interaction.Multiple(testTag, inputs)
                        )
                    }

                    in clickables -> {
                        val testNoClicks = settings.forNode.firstOrNull { it.nodeTag == testTag }?.rules?.applyClickIgnore
                            ?: settings.globalRules.applyClickIgnore

                        val clicks = mutableListOf<InteractionType.Clickable>()
                        if (testNoClicks) {
                            clicks.add(InteractionType.Clickable.NoClick)
                        }
                        clicks.add(InteractionType.Clickable.PerformClick)

                        nodes.add(
                            Interaction.Multiple(testTag, clicks)
                        )
                    }

                    in visibles -> {
                        nodes.add(
                            Interaction.Single(testTag, InteractionType.Visibility)
                        )
                    }

                    else -> {
                        val resolvedFunction = try {
                            ktCallExpression.reference?.resolve() as? KtNamedFunction
                        } catch (e: Exception) {
                            null
                        }

                        resolvedFunction?.let { declaration ->
                            val nestedFunctions = findComposableFunctionsInBody(declaration)
                            nestedFunctions.forEach { nestedFunction ->
                                val nestedInteractions = defineInteractionType(nestedFunction)
                                nodes.addAll(nestedInteractions)
                            }
                        }
                    }
                }
            }
        }

        return nodes
    }

    private fun generateAllCombinations(nodes: List<Interaction>): List<List<TestNode>> {
        if (nodes.isEmpty()) return emptyList()

        val combinations = mutableListOf<List<TestNode>>()
        generateCombinationsRecursive(nodes, 0, mutableListOf(), combinations)
        return combinations
    }

    private fun generateCombinationsRecursive(
        nodes: List<Interaction>,
        index: Int,
        currentCombination: MutableList<TestNode>,
        combinations: MutableList<List<TestNode>>
    ) {
        if (index == nodes.size) {
            combinations.add(currentCombination.toList())
            return
        }

        when (val interaction = nodes[index]) {
            is Interaction.Single -> {
                currentCombination.add(TestNode(interaction.testTag, interaction.interaction))
                generateCombinationsRecursive(nodes, index + 1, currentCombination, combinations)
                currentCombination.removeAt(currentCombination.size - 1)
            }
            is Interaction.Multiple -> {
                interaction.interactions.forEach { interactionType ->
                    currentCombination.add(TestNode(interaction.testTag, interactionType))
                    generateCombinationsRecursive(nodes, index + 1, currentCombination, combinations)
                    currentCombination.removeAt(currentCombination.size - 1)
                }
            }
        }
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

    private fun findFunctionDeclaration(functionName: String): KtNamedFunction? {
        val psiManager = PsiManager.getInstance(project)
        val projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        val ktFiles = mutableListOf<KtNamedFunction>()

        projectFileIndex.iterateContent { virtualFile ->
            if (!virtualFile.isDirectory && scope.contains(virtualFile)) {
                val psiFile = psiManager.findFile(virtualFile)
                if (psiFile != null && psiFile is KtNamedFunction && (psiFile as KtNamedFunction).name == functionName) {
                    ktFiles.add(psiFile)
                }
            }
            true
        }

        if (ktFiles.isNotEmpty()) {
            return ktFiles.firstOrNull() // Return the first function found
        }

        return null
    }

    private fun getKeyboardType(element: PsiElement): KeyboardType {
        when (element) {
            is KtNamedFunction -> {
                // Check function declaration parameters
                val keyboardOptionsParameter = element.valueParameters.find {
                    it.name == "keyboardOptions"
                }
                if (keyboardOptionsParameter != null) {
                    // Find the default value of keyboardOptions parameter
                    val defaultValue = keyboardOptionsParameter.defaultValue?.text ?: ""
                    val matchResult = Regex("keyboardType\\s*=\\s*KeyboardType\\.(\\w+)").find(defaultValue)
                    matchResult?.let {
                        val keyboardType = it.groupValues[1]
                        return KeyboardType.valueOf(keyboardType)
                    }
                }
            }
            is KtCallExpression -> {
                // Check function call arguments
                val functionReference = element.reference
                val resolvedFunction = functionReference?.resolve() as? KtNamedFunction
                    ?: return KeyboardType.Text // Couldn't resolve function reference

                val keyboardOptionsParameter = resolvedFunction.valueParameters.find {
                    it.name == "keyboardOptions"
                }
                if (keyboardOptionsParameter != null) {
                    // Find the default value of keyboardOptions parameter
                    val defaultValue = keyboardOptionsParameter.defaultValue?.text ?: ""
                    val matchResult = Regex("keyboardType\\s*=\\s*KeyboardType\\.(\\w+)").find(defaultValue)
                    matchResult?.let {
                        val keyboardType = it.groupValues[1]
                        return KeyboardType.valueOf(keyboardType)
                    }
                }
            }
        }
        // If keyboardOptions parameter is not present or no keyboardType specified, return default Text
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
                parameters.add(Parameter(name = paramName, klazz = paramClass))
            }
        }

        return parameters
    }

    private fun findClassByName(kotlinType: KotlinType): KtClass? {
        val classDescriptor = kotlinType.constructor.declarationDescriptor as? ClassDescriptor
        val fqName = classDescriptor?.fqNameSafe

        return if (fqName != null) {
            val project = file.project
            val psiClass = KotlinFullClassNameIndex.getInstance()
                .get(fqName.asString(), project, GlobalSearchScope.allScope(project))
                .firstOrNull() as? KtClassOrObject
            psiClass as? KtClass
        } else null
    }

    /*fun generateMockCode(parameters: List<Parameter>, mockData: List<List<Any>>): List<String> {
        val codeList = mutableListOf<String>()

        mockData.forEachIndexed { index, params ->
            val code = StringBuilder()
            var paramIndex = 0
            val mocks = mockData[index]

            parameters.forEach { parameter ->
                val paramName = parameter.name
                val paramClass = parameter.klazz

                if (paramClass.isData()) {
                    val mockName = "${paramName}Mock$index"
                    code.append("val $mockName = mock<${paramClass.name}> {\n")
                    paramClass.primaryConstructorParameters.forEach { constructorParam ->
                        val propertyName = constructorParam.name
                        val startValue = try {
                            mocks[paramIndex]
                        } catch (e: Exception) {
                            null
                        }
                        val value = if (startValue is String && startValue.isEmpty()) {
                            "\"\""
                        } else startValue

                        code.append("    whenever(it.$propertyName).thenReturn($value)\n")
                        paramIndex++
                    }
                    code.append("}\n")
                } else {
                    code.append("val ${paramName}Mock = ${mocks[paramIndex]}")
                    paramIndex++
                }
            }
            codeList.add(code.toString())
        }

        return codeList
    }*/
}
