package com.ordinarythinker.jcat.enums

import org.jetbrains.kotlin.psi.KtClass

sealed class ParameterType {
    data class Type(
        val clazz: KtClass
    ) : ParameterType()

    data object Function : ParameterType()
}