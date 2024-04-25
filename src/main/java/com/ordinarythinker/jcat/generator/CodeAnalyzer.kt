package com.ordinarythinker.jcat.generator

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.ordinarythinker.jcat.enums.KeyboardType
import com.ordinarythinker.jcat.enums.VisualTransformationType
import com.ordinarythinker.jcat.models.TestScenario
import com.ordinarythinker.jcat.utils.isComposableAnnotation
import org.jetbrains.kotlin.psi.*
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberProperties

class CodeAnalyzer(
    private val file: KtFile,
) {
    private val functions = mutableListOf<KtNamedFunction>()
    private val tests = mutableListOf<TestScenario>()

    fun analyze(): List<TestScenario> {
        // TODO: CodeAnalyzer.analyze() is waiting for implementation
        findComposables()
        return tests;
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

    fun getVisualTransformationType(project: Project, element: PsiElement): VisualTransformationType? {
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

    fun findFunctionDeclaration(project: Project, functionName: String): KtNamedFunction? {
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

    fun getKeyboardType(project: Project, element: PsiElement): KeyboardType {
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

    private fun <T : Any> generateMockDataFromClass(clazz: KClass<T>): T? {
        return when (clazz) {
            String::class -> "MockString" as T
            Int::class -> 0 as T
            Long::class -> 0L as T
            Float::class -> 0.0f as T
            Double::class -> 0.0 as T
            Boolean::class -> false as T
            List::class -> listOf<Any>() as T
            Map::class -> mapOf<Any, Any>() as T
            else -> {
                if (clazz.isData) {
                    val properties = clazz.memberProperties
                    val instance = clazz.createInstance()
                    for (property in properties) {
                        if (property is KMutableProperty<*>) {
                            val mockValue = generateMockDataFromClass(property.returnType.classifier as KClass<*>)
                            (property as KMutableProperty<Any?>).setter.call(instance, mockValue)
                        }
                    }
                    instance
                } else {
                    null
                }
            }
        }
    }
}