package com.ordinarythinker.jcat

import com.ordinarythinker.jcat.generator.TestRule
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction

data class FunctionTest(
        val function: KtFunction,
        val rule: TestRule,
        val ruleExpression: KtExpression,
        val mocks: List<KtExpression> = listOf()
)
