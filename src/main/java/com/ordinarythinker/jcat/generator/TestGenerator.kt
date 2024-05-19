package com.ordinarythinker.jcat.generator

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.ordinarythinker.jcat.enums.InteractionType
import com.ordinarythinker.jcat.models.FunctionTest
import com.ordinarythinker.jcat.models.TestNode
import com.ordinarythinker.jcat.utils.Cons.ASSERT
import com.ordinarythinker.jcat.utils.Cons.ASSERT_IS_DISPLAYED
import com.ordinarythinker.jcat.utils.Cons.COMPOSE_TEST_RULE
import com.ordinarythinker.jcat.utils.Cons.HAS_TEXT
import com.ordinarythinker.jcat.utils.Cons.ON_NODE_WITH_TAG
import com.ordinarythinker.jcat.utils.Cons.PERFORM_CLICK
import com.ordinarythinker.jcat.utils.Cons.PERFORM_TEXT_INPUT
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

class TestGenerator(
    private val project: Project,
    private val packageName: String
) {
    fun generateTests(ktFile: KtFile) {
        val codeAnalyzer = CodeAnalyzer(
            project = project,
            file = ktFile
        )

        val tests = codeAnalyzer.analyze()
        generateTestFile(tests)
    }

    fun generateTestFile(tests: List<FunctionTest>) {
        val codeStyleManager = CodeStyleManager.getInstance(project)

        val fileText = buildTestFileText(tests)
        val fileName = "${getClassName(tests)}.kt"

        val psiFile = PsiFileFactory.getInstance(project)
            .createFileFromText(fileName, KotlinFileType.INSTANCE as FileType, fileText)

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
            val testFile = packageDir.createChildData(this, fileName)
            testFile.setBinaryContent(psiFile.text.toByteArray())
        }
    }

    private fun buildTestFileText(scenarios: List<FunctionTest>): String {
        val name = getClassName(scenarios)
        val imports: List<String> = getImports(scenarios)

        val content = buildString {
            appendLine(
                createFileHeader(imports)
            )

            appendLine()
            appendLine("@RunWith(AndroidJUnit4::class)")
            appendLine("class $name {")
            appendLine()
            appendLine("    @get:Rule")
            appendLine("    val $COMPOSE_TEST_RULE = createComposeRule()")

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

    private fun getTestScenario(test: FunctionTest): String {
        return buildString {
            // TODO: getTestScenario
            test.scenarios.forEach { scenario ->
                appendLine("    @Test")
                appendLine("    fun `${testScenarioToTestFunctionName(scenario)}`() {")
                test.mocks.forEach { appendLine(it) }
                appendLine()
                appendLine("        $COMPOSE_TEST_RULE.setContent {")
                appendLine("            ${test.function.name} (")
                test.parameters.map { "                $it = ${it}Mock" }.joinToString(separator = ",\n")
                appendLine("            )")

                appendLine(toScenarioCode(scenario))

                appendLine("        }")
                appendLine()

                appendLine("    }")
            }
        }
    }

    private fun toScenarioCode(scenario: List<TestNode>): String {
        return buildString {
            scenario.forEach { node ->
                val string = "${COMPOSE_TEST_RULE}.${ON_NODE_WITH_TAG}(\"${node.testTag}\")"
                when (node.rule) {
                    InteractionType.Clickable.NoClick -> {}
                    InteractionType.Clickable.PerformClick -> {
                        appendLine("$string.$PERFORM_CLICK()")
                    }
                    InteractionType.Input.NoInput -> {}
                    is InteractionType.Input.NumberInput -> {
                        appendLine("$string.$PERFORM_TEXT_INPUT(${node.rule.value})")
                        appendLine("$string.$ASSERT($HAS_TEXT(${node.rule.value}))")
                    }
                    is InteractionType.Input.RandomStringInput -> {
                        appendLine("$string.$PERFORM_TEXT_INPUT(${node.rule.value})")
                        appendLine("$string.$ASSERT($HAS_TEXT(${node.rule.value}))")
                    }
                    is InteractionType.Input.ValidEmailInput -> {
                        appendLine("$string.$PERFORM_TEXT_INPUT(${node.rule.value})")
                        appendLine("$string.$ASSERT($HAS_TEXT(${node.rule.value}))")
                    }
                    InteractionType.Visibility -> {
                        appendLine("$string.$ASSERT_IS_DISPLAYED()")
                    }
                }
            }
        }
    }

    private fun testScenarioToTestFunctionName(scenario: List<TestNode>): String {
        return scenario.map { node ->
            "${node.testTag}: ${node.rule.toDescription()}"
        }.joinToString(",")
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
                appendLine("import $it")
            }
        }
    }

    private fun getClassName(scenarios: List<FunctionTest>): String {
        return if (scenarios.isNotEmpty()) {
            "${scenarios[0].function.name}Test"
        } else ""
    }

    private fun getImports(scenarios: List<FunctionTest>): List<String> {
        val imports = mutableListOf<String>()

        scenarios.forEach { scenario ->
            imports.addAll(scenario.imports)
        }

        return imports.distinct()
    }
}
