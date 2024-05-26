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
import com.ordinarythinker.jcat.settings.Settings
import com.ordinarythinker.jcat.utils.Cons.ASSERT
import com.ordinarythinker.jcat.utils.Cons.ASSERT_IS_DISPLAYED
import com.ordinarythinker.jcat.utils.Cons.COMPOSE_TEST_RULE
import com.ordinarythinker.jcat.utils.Cons.HAS_TEXT
import com.ordinarythinker.jcat.utils.Cons.ON_NODE_WITH_TAG
import com.ordinarythinker.jcat.utils.Cons.PERFORM_CLICK
import com.ordinarythinker.jcat.utils.Cons.PERFORM_TEXT_INPUT
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import java.util.*

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

    fun generateTestFile(functionTests: List<FunctionTest>) {
        val codeStyleManager = CodeStyleManager.getInstance(project)

        val fileText = buildTestFileText(functionTests)
        val fileName = "${getClassName(functionTests)}.kt"

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

        ApplicationManager.getApplication().runWriteAction {
            for (part in parts) {
                packageDir = packageDir.findChild(part) ?: packageDir.createChildDirectory(project, part)
            }

            val testFile = packageDir.createChildData(this, fileName)
            testFile.setBinaryContent(psiFile.text.toByteArray())
        }
    }

    private fun buildTestFileText(functionTests: List<FunctionTest>): String {
        val name = getClassName(functionTests)
        val imports: List<String> = getImports(functionTests)

        val content = buildString {
            appendLine(
                createFileHeader(imports)
            )

            appendLine()
            appendLine("@Suppress(\"IllegalIdentifier\")")
            appendLine("@RunWith(AndroidJUnit4::class)")
            appendLine("class $name {")
            appendLine()
            appendLine("    @get:Rule")
            appendLine("    val $COMPOSE_TEST_RULE = createComposeRule()")

            functionTests.forEach { functionTest ->
                appendLine()
                appendLine(
                    getTestScenario(functionTest)
                )
                appendLine()
            }

            appendLine("}")
        }

        return content
    }

    private fun getTestScenario(test: FunctionTest): String {
        var index = 0
        return buildString {
            // TODO: getTestScenario
            test.mocks.forEach { mocks ->
                test.scenarios.forEach { scenario ->
                    if (scenario.isNotEmpty()) {
                        appendLine("    @Test")
                        appendLine("    fun `${testScenarioToTestFunctionName(scenario)} ${index++}`() {")
                        appendLine()
                        appendLine("        $COMPOSE_TEST_RULE.setContent {")
                        appendLine("            ${test.function.name} (")
                        appendLine(mocks)
                        appendLine("            )")
                        appendLine("        }")
                        appendLine()
                        appendLine(
                            toScenarioCode(scenario)
                        )
                        appendLine("    }")
                    }
                }
            }
        }
    }

    private fun toScenarioCode(scenario: List<TestNode>): String {
        return buildString {
            scenario.forEach { node ->
                val string = "${COMPOSE_TEST_RULE}.${ON_NODE_WITH_TAG}(${node.testTag})"
                when (node.rule) {
                    InteractionType.Clickable.NoClick -> {}
                    InteractionType.Clickable.PerformClick -> {
                        appendLine("$string.$PERFORM_CLICK()")
                    }
                    InteractionType.Input.NoInput -> {}
                    is InteractionType.Input.NumberInput -> {
                        appendLine("$string.$PERFORM_TEXT_INPUT(\"${node.rule.value}\")")
                        appendLine("$string.$ASSERT($HAS_TEXT(\"${node.rule.value}\"))")
                    }
                    is InteractionType.Input.RandomStringInput -> {
                        appendLine("$string.$PERFORM_TEXT_INPUT(\"${node.rule.value}\")")
                        appendLine("$string.$ASSERT($HAS_TEXT(\"${node.rule.value}\"))")
                    }
                    is InteractionType.Input.ValidEmailInput -> {
                        appendLine("$string.$PERFORM_TEXT_INPUT(\"${node.rule.value}\")")
                        appendLine("$string.$ASSERT($HAS_TEXT(\"${node.rule.value}\"))")
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
            "${node.testTag} ${node.rule.toDescription()}"
        }.joinToString(" and ")
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
            appendLine(
                """
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import androidx.compose.ui.test.assert
                    import androidx.compose.ui.test.assertIsDisplayed
                    import androidx.compose.ui.test.hasText
                    import androidx.compose.ui.test.junit4.createComposeRule
                    import androidx.compose.ui.test.onNodeWithTag
                    import androidx.compose.ui.test.performClick
                    import androidx.compose.ui.test.performTextInput
                    import androidx.test.ext.junit.runners.AndroidJUnit4
                    import org.junit.Rule
                    import org.junit.Test
                    import org.junit.runner.RunWith
                """.trimIndent()
            )

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

    private fun composeTests(): String {
        val settings: Settings = Settings.init(project)
        val random = Random()
        val emptyString = "\"\""
        val possibleNames = listOf("\"John\"", emptyString)
        val possibleAges = listOf(-1, random.nextInt(100))
        val possibleEmails = listOf("\"example@email.com\"", "\"92mlajsdkajlwkqk\"", emptyString)
        val possibleUrls = listOf("\"alkdpoqwoiepoqwie\"", emptyString)

        return buildString {
            appendLine(
                """
                    import androidx.compose.runtime.getValue
                    import androidx.compose.runtime.mutableStateOf
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.setValue
                    import androidx.compose.ui.test.assert
                    import androidx.compose.ui.test.assertIsDisplayed
                    import androidx.compose.ui.test.hasText
                    import androidx.compose.ui.test.junit4.createComposeRule
                    import androidx.compose.ui.test.onNodeWithTag
                    import androidx.compose.ui.test.performClick
                    import androidx.compose.ui.test.performTextInput
                    import androidx.test.ext.junit.runners.AndroidJUnit4
                    import io.jctest.app.models.ProfileViewData
                    import io.jctest.app.ui.screen.Screen
                    import org.junit.Rule
                    import org.junit.Test
                    import org.junit.runner.RunWith
                """.trimIndent()
            )

            appendLine()

            appendLine(
                """
                @RunWith(AndroidJUnit4::class)
                class ScreenTest {

                    @get: Rule
                    val composeTestRule = createComposeRule()
                """.trimIndent()
            )

            appendLine()

            for (i in 1..100) {
                val name = possibleNames.random()
                val age = possibleAges.random()
                val avatarSrc = possibleUrls.random()
                val email = possibleEmails.random()
                val isUserDataValid = random.nextBoolean()
                val performInput = random.nextBoolean()
                val performClickEdit = random.nextBoolean()
                val performClickSubmit = random.nextBoolean()

                if (!settings.globalRules.applyClickIgnore && (!performClickEdit || !performClickSubmit)) continue
                if (!settings.globalRules.useEmptyStrings && (email == emptyString || name == emptyString || avatarSrc == emptyString)) continue

                val text = """
                    @Test
                    fun test$i() {
                    composeTestRule.setContent {
                        var email by remember {
                            mutableStateOf("")
                        }

                        Screen(
                            user = ProfileViewData(
                                name = $name,
                                age = $age,
                                avatarSrc = $avatarSrc
                            ),
                            isUserDataValid = $isUserDataValid,
                            email = "",
                            onEmailChange = { email = it },
                            onEditClick = {},
                            onSubmitClick = {}
                        )
                    }

                    composeTestRule.onNodeWithTag("image").assertIsDisplayed()
                    composeTestRule.onNodeWithTag("user_name_text").assertIsDisplayed()
                    composeTestRule.onNodeWithTag("user_age_text").assertIsDisplayed()
                """.trimIndent()
                appendLine(text)

                if (!settings.excludeTags.contains("edit_button") && ((settings.globalRules.applyClickIgnore && !performClickEdit) || settings.globalRules.applyClickIgnore && performClickEdit)) {
                    appendLine("composeTestRule.onNodeWithTag(\"edit_button\").performClick()")
                }

                if (!settings.excludeTags.contains("email_text_field") && (settings.globalRules.useEmptyStrings && email == emptyString) && performInput) {
                    val value = if (email != emptyString) "\"$email\"" else email
                    appendLine("composeTestRule.onNodeWithTag(\"email_text_field\").performTextInput($value)")
                    appendLine("composeTestRule.onNodeWithTag(\"email_text_field\").assert(hasText($value))")
                }

                if (!settings.excludeTags.contains("submit_button") && ((settings.globalRules.applyClickIgnore && !performClickSubmit) || settings.globalRules.applyClickIgnore && performClickSubmit)) {
                    appendLine("composeTestRule.onNodeWithTag(\"submit_button\").performClick()")
                }

                appendLine("}")

                appendLine()
            }
            appendLine("}")
        }
    }
}
