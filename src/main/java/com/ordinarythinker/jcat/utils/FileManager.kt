package com.ordinarythinker.jcat.utils

import com.intellij.openapi.project.Project
import com.ordinarythinker.jcat.models.TestScenario

class FileManager(
        private val project: Project,
        private val packageName: String,
    ) {
    fun writeTests(tests: List<TestScenario>) {
        // TODO
    }
}