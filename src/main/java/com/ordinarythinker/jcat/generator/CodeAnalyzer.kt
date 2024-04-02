package com.ordinarythinker.jcat.generator

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.ordinarythinker.jcat.models.TestScenario
import com.ordinarythinker.jcat.utils.isComposableAnnotation
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

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

    private fun analyzeContext(function: KtNamedFunction) : KtExpression? {
        // TODO: CodeAnalyzer.analyzeContext() is waiting for implementation
        return null
    }

    private fun makeMocks() {
        // TODO: CodeAnalyzer.makeMocks() is waiting for implementation
    }
}