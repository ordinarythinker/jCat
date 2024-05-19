package com.ordinarythinker.jcat.models

import org.jetbrains.kotlin.psi.KtFunction

data class FunctionTest(
    val function: KtFunction,
    val mocks: List<String> = listOf(),
    val imports: List<String> = listOf(),
    val testNodes: List<List<TestNode>> = listOf()
)
