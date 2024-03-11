package com.ordinarythinker.jcat.utils

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.ordinarythinker.jcat.FunctionTest
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtNamedFunction

class FileManager(
        private val project: Project,
        private val packageName: String,
    ) {
    fun writeTests(tests: List<FunctionTest>) {
        val fileText = buildTestFileText(tests, packageName)
        val psiFile = PsiFileFactory.getInstance(project)
            .createFileFromText("MyComposeTest.kt", KotlinFileType.INSTANCE as FileType, fileText)

        // Get the directory for androidTest
        val androidTestDir = project.baseDir.findChild("app")?.findChild("src")?.findChild("androidTest")
            ?: createAndroidTestDirectory()

        val javaDir = androidTestDir.findChild("java") ?: androidTestDir.createChildDirectory(project, "java")

        // Get the package directory within androidTest
        var packageDir = javaDir
        val parts = packageName.split(".")

        for (part in parts) {
            packageDir = packageDir.findChild(part) ?: packageDir.createChildDirectory(project, part)
        }

        // Save the test file
        psiFile.virtualFile.copy(this, packageDir, "MyComposeTest.kt")
    }

    private fun buildTestFileText(composableFunctions: List<FunctionTest>, packageName: String): String {
        val content = buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import androidx.compose.ui.test.*")
            appendLine("import androidx.compose.ui.test.junit4.createComposeRule")
            appendLine("import androidx.compose.ui.test.junit4.setContent")
            appendLine("import org.junit.Rule")
            appendLine("import org.junit.Test")
            appendLine()
            appendLine("class MyComposeTest {")
            appendLine()
            appendLine("    @get:Rule")
            appendLine("    val composeTestRule = createComposeRule()")
            appendLine()
            appendLine("    @Test")
            appendLine("    fun myTest() {")
            appendLine("        // Start the app")
            appendLine("        composeTestRule.setContent {")
            appendLine("            // Your Compose content setup here")
            appendLine("        }")
            appendLine()

            for (function in composableFunctions) {
                appendLine("        // Test for ${function.function.name}")
                appendLine("        // composeTestRule.onNode(...).perform...")
                appendLine("        // composeTestRule.onNode(...).assert...")
                appendLine()
            }

            appendLine("    }")
            appendLine("}")
        }
        return content
    }

    private fun createAndroidTestDirectory(): VirtualFile {
        val appDir = project.baseDir.findChild("app")
            ?: throw IllegalStateException("app directory not found")
        val srcDir = appDir.createChildDirectory(project, "src")
        return srcDir.createChildDirectory(project, "androidTest")
    }
}