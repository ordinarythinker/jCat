package com.ordinarythinker.jcat.models

import kotlin.reflect.KClass

data class Parameter(
    val name: String,
    val klazz: KClass<*>
)