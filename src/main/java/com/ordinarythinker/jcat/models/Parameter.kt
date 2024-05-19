package com.ordinarythinker.jcat.models

import org.jetbrains.kotlin.psi.KtClass

data class Parameter(
    val name: String,
    val klazz: KtClass
)