package com.ordinarythinker.jcat.generator

import com.ordinarythinker.jcat.FunctionTest
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class CodeAnalyzer(
    private val file: KtFile,
) {
    private val functions = mutableListOf<KtNamedFunction>()
    private val tests = mutableListOf<FunctionTest>()

    fun analyze() : List<FunctionTest> {
        // TODO: CodeAnalyzer.analyze() is waiting for implementation
        findComposables()
        return tests;
    }

    private fun findComposables() {
        // TODO: CodeAnalyzer.findComposables() is waiting for implementation
    }

    private fun analyzeContext(function: KtNamedFunction) : KtExpression? {
        // TODO: CodeAnalyzer.analyzeContext() is waiting for implementation
        return null
    }

    private fun makeMocks() {
        // TODO: CodeAnalyzer.makeMocks() is waiting for implementation
    }
}