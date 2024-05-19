package com.ordinarythinker.jcat.models

import org.jetbrains.kotlin.psi.KtFunction

data class FunctionTest(
    val function: KtFunction,
    val parameters: List<String> = listOf(),
    val mocks: List<String> = listOf(),
    val imports: List<String> = listOf(),
    val scenarios: List<List<TestNode>> = listOf()
)
