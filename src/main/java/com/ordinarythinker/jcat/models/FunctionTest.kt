package com.ordinarythinker.jcat.models

import org.jetbrains.kotlin.psi.KtFunction

data class FunctionTest(
    val function: KtFunction,
    val mocks: List<String> = listOf(),
    val imports: List<ImportItem> = listOf(),
    val testNodes: List<TestNode> = listOf()
)
