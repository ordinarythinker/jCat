package com.ordinarythinker.jcat.models

import com.ordinarythinker.jcat.enums.InteractionType

data class TestNode(
    val testTag: String,
    val rule: InteractionType
)