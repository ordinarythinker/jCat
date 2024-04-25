package com.ordinarythinker.jcat.models

import com.ordinarythinker.jcat.enums.InteractionType

data class TestNode(
    val nodeName: String,
    val rule: InteractionType
)