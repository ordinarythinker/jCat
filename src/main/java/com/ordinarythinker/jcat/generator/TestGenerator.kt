package com.ordinarythinker.jcat.generator

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.ordinarythinker.jcat.models.FunctionTest
import com.ordinarythinker.jcat.settings.Settings
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

class TestGenerator(
    private val project: Project,
    private val packageName: String
) {
    private val settings: Settings = Settings.init(project)

    fun generateTests(ktFile: KtFile) {

    }

    // TODO: replace by generateTests method
    fun generateTestFile(composableFunctions: List<FunctionTest>) {
        val codeStyleManager = CodeStyleManager.getInstance(project)

        val fileText = buildTestFileText(composableFunctions)

        val psiFile = PsiFileFactory.getInstance(project)
            .createFileFromText("MyComposeTest.kt", KotlinFileType.INSTANCE as FileType, fileText)

        codeStyleManager.scheduleReformatWhenSettingsComputed(psiFile)

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

        ApplicationManager.getApplication().runWriteAction {
            val testFile = packageDir.createChildData(this, "MyComposeTest.kt")
            testFile.setBinaryContent(psiFile.text.toByteArray())
        }
    }

    private fun buildTestFileText(scenarios: List<FunctionTest>): String {
        val name = scenarios
        val imports: List<String> = getImports(scenarios)

        val content = buildString {
            appendLine(
                createFileHeader(imports)
            )

            appendLine()
            appendLine("@RunWith(AndroidJUnit4::class)")
            appendLine("class ${getFileName(scenarios)} {")
            appendLine()
            appendLine("    @get:Rule")
            appendLine("    val composeTestRule = createComposeRule()")

            scenarios.forEach { scenario ->
                appendLine()
                appendLine(
                    getTestScenario(scenario)
                )
                appendLine()
            }

            appendLine("}")
        }

        return content
    }

    private fun getTestScenario(scenario: FunctionTest): String {
        return buildString {
            // TODO: getTestScenario
            appendLine("    @Test")
            appendLine("    fun myTest() {")
            appendLine("        // Start the app")
            appendLine("        composeTestRule.setContent {")
            appendLine("            // Your Compose content setup here")
            appendLine("        }")
            appendLine()

            appendLine("    }")
        }
    }

    private fun createAndroidTestDirectory(): VirtualFile {
        val appDir = project.baseDir.findChild("app")
            ?: throw IllegalStateException("app directory not found")
        val srcDir = appDir.createChildDirectory(project, "src")
        return srcDir.createChildDirectory(project, "androidTest")
    }

    private fun createFileHeader(imports: List<String>): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import androidx.compose.ui.input.key.Key")
            appendLine("import androidx.compose.ui.test.*")
            appendLine("import androidx.compose.ui.test.junit4.createComposeRule")
            appendLine("import androidx.compose.ui.test.junit4.setContent")
            appendLine("import androidx.test.ext.junit.runners.AndroidJUnit4")
            appendLine("import org.junit.Rule")
            appendLine("import org.junit.Test")
            appendLine("import org.junit.runner.RunWith")
            appendLine("import org.mockito.kotlin.mock")
            appendLine("import org.mockito.kotlin.whenever")

            imports.forEach {
                appendLine(it)
            }
        }
    }

    private fun getFileName(scenarios: List<FunctionTest>): String {
        return if (scenarios.isNotEmpty()) {
            "${scenarios[0].function.name}Test"
        } else ""
    }

    private fun getImports(scenarios: List<FunctionTest>): List<String> {
        val imports = mutableListOf<String>()

        scenarios.forEach { scenario ->
            scenario.imports.forEach { imp ->
                imports.add(
                    "${imp.packagePath}.${imp.functionName}"
                )
            }
        }

        return imports.distinct()
    }
}
