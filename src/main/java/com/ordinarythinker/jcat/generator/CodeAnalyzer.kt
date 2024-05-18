package com.ordinarythinker.jcat.generator

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.ordinarythinker.jcat.enums.KeyboardType
import com.ordinarythinker.jcat.enums.VisualTransformationType
import com.ordinarythinker.jcat.models.FunctionTest
import com.ordinarythinker.jcat.models.Parameter
import com.ordinarythinker.jcat.models.TestNode
import com.ordinarythinker.jcat.settings.Settings
import com.ordinarythinker.jcat.utils.Cons.FUNCTION_IMAGE
import com.ordinarythinker.jcat.utils.Cons.FUNCTION_TEXT
import com.ordinarythinker.jcat.utils.Cons.emptyString
import com.ordinarythinker.jcat.utils.isComposableAnnotation
import com.ordinarythinker.jcat.utils.toKClass
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmErasure

class CodeAnalyzer(
    private val project: Project,
    private val file: KtFile,
) {
    private val mocker = Mocker()
    private val settings: Settings = Settings.init(project)

    private val functions = mutableListOf<KtNamedFunction>()
    private val tests = mutableListOf<FunctionTest>()

    fun analyze(): List<FunctionTest> {
        // TODO: CodeAnalyzer.analyze() is waiting for implementation
        findComposableDeclarations()
        // for each composable
        // - extract parameters         ✔
        // - make mocks                 ✔
        // - define composable calls    ✔
        // - define their modifiers: if testTag is present, define type;
        // if not the Image, Text or TextField, dig into the function but without creation mock for input params
        // - in the sub calls define testTags and that's it
        // - generate test
        // - first iteration is ended

        functions.forEach { function ->
            val parameters = extractParameters(function)
            val mocks = mocker.generateMockData(parameters)
            val subFunctions = findComposableFunctionsInBody(function)
            val imports = mutableListOf<String>()

            val interactionsForScreen = mutableListOf<TestNode>()
            subFunctions.forEach { uiComponent ->
                val result = defineInteractionType(uiComponent)

                interactionsForScreen.addAll(result.first)
                imports.addAll(result.second)
            }
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

    private fun defineInteractionType(ktCallExpression: KtCallExpression): Pair<List<TestNode>, List<String>> {
        val nodes = mutableListOf<TestNode>()
        val imports = mutableListOf<String>()
        val name = ktCallExpression.name ?: emptyString
        val testTag = retrieveTestTagFromComposable(ktCallExpression)

        when {
            name != emptyString && (name == FUNCTION_IMAGE || name == FUNCTION_TEXT) -> {
                testTag?.let { tag ->

                }
            }
        }

        return nodes to imports
    }

    private fun retrieveTestTagFromComposable(callExpression: KtCallExpression): String? {
        // Find the Modifier.testTag call within the given composable call expression
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

    private fun makeMocks() {
        // TODO: CodeAnalyzer.makeMocks() is waiting for implementation
    }

    fun extractParameters(function: KtNamedFunction): List<Parameter> {
        return function.valueParameters.map { parameter ->
            val klazz = parameter.typeReference?.typeElement as KtClass
            Parameter(parameter.name ?: "", klazz.toKClass())
        }
    }

    fun generateMockCode(parameters: List<Parameter>, mockData: List<List<Any>>): List<String> {
        val codeList = mutableListOf<String>()

        mockData.forEachIndexed { index, params ->
            val code = StringBuilder()
            params.forEachIndexed { paramIndex, paramValue ->
                val parameter = parameters[paramIndex]
                val paramName = parameter.name
                val paramClass = parameter.klazz

                when {
                    paramClass.isData -> {
                        val mockName = "${paramName}Mock$index"
                        code.append("val $mockName = mock<${paramClass.simpleName}> {\n")
                        paramClass.primaryConstructor?.parameters?.forEach { constructorParam ->
                            val property = paramClass.declaredMemberProperties.first { it.name == constructorParam.name }
                            val propertyName = property.name
                            code.append("    whenever(it.$propertyName).thenReturn(params[$paramIndex] as ${property.returnType.jvmErasure.simpleName})\n")
                        }
                        code.append("}\n")
                    }
                    paramClass == String::class || paramClass == Int::class || paramClass == Boolean::class -> {
                        code.append("val ${paramName}Mock = params[$paramIndex] as ${paramClass.simpleName}\n")
                    }
                    paramClass.isSubclassOf(Collection::class) -> {
                        code.append("val ${paramName}Mock = params[$paramIndex] as ${paramClass.simpleName}\n")
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported type: ${paramClass.simpleName}")
                    }
                }
            }
            codeList.add(code.toString())
        }

        return codeList
    }
}