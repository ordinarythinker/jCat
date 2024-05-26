package com.ordinarythinker.jcat.models

import com.ordinarythinker.jcat.enums.ParameterType

data class Parameter(
    val name: String,
    val type: ParameterType
)