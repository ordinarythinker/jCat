package com.ordinarythinker.jcat.generator

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.ordinarythinker.jcat.enums.KeyboardType
import com.ordinarythinker.jcat.enums.VisualTransformationType
import com.ordinarythinker.jcat.models.FunctionTest
import com.ordinarythinker.jcat.models.Parameter
import com.ordinarythinker.jcat.utils.isComposableAnnotation
import org.jetbrains.kotlin.psi.*
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmErasure

class CodeAnalyzer(
    private val project: Project,
    private val file: KtFile,
) {
    private val functions = mutableListOf<KtNamedFunction>()
    private val tests = mutableListOf<FunctionTest>()
    private val mocker = Mocker()

    fun analyze(): List<FunctionTest> {
        // TODO: CodeAnalyzer.analyze() is waiting for implementation
        findComposables()
        // for each composable
        // - extract parameters
        // - make mocks
        // - define modifiers

        return tests
    }

    private fun findComposables() {
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

    private fun analyzeContext(function: KtNamedFunction): KtExpression? {
        // TODO: CodeAnalyzer.analyzeContext() is waiting for implementation
        return null
    }

    private fun makeMocks() {
        // TODO: CodeAnalyzer.makeMocks() is waiting for implementation
    }

    fun extractParameters(function: KFunction<*>): List<Parameter> {
        return function.parameters.drop(1).map { parameter ->
            Parameter(parameter.name ?: "unknown", parameter.type.jvmErasure)
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