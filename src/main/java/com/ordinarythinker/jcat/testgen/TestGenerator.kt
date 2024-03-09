package com.ordinarythinker.jcat.testgen

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtNamedFunction

class TestGenerator(private val project: Project) {
    fun generateTestFile(composableFunctions: List<KtNamedFunction>, packageName: String) {
        val fileText = buildTestFileText(composableFunctions, packageName)
        val psiFile = PsiFileFactory.getInstance(project)
            .createFileFromText("MyComposeTest.kt", KotlinFileType.INSTANCE as FileType, fileText)

        // Get the directory for androidTest
        val androidTestDir = project.baseDir.findChild("app")?.findChild("src")?.findChild("androidTest")
            ?: createAndroidTestDirectory()

        // Get the package directory within androidTest
        val packageDir = androidTestDir.createChildDirectory(project, packageName.replace('.', '/'))

        // Save the test file
        psiFile.virtualFile.copy(this, packageDir, "MyComposeTest.kt")
    }

    private fun buildTestFileText(composableFunctions: List<KtNamedFunction>, packageName: String): String {
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
                appendLine("        // Test for ${function.name}")
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
