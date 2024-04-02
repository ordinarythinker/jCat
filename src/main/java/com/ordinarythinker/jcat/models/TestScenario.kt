package com.ordinarythinker.jcat.models

import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction

data class TestScenario(
    val function: KtFunction,
    val ruleExpression: KtExpression,
    val mocks: List<KtExpression> = listOf(),
    val imports: List<ImportItem> = listOf(),
    val testNodes: List<TestNode> = listOf()
)
