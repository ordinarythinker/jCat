package com.ordinarythinker.jcat.models

import org.junit.rules.TestRule

data class TestNode(
    val nodeName: String,
    val rule: TestRule
)