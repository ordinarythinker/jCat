package com.ordinarythinker.jcat.settings

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.ordinarythinker.jcat.utils.Cons
import java.io.File
import java.io.IOException

data class Settings(
    val excludeTags: List<String>,
    val globalRules: Rules,
    val forNode: List<SettingsNode>
) {
    companion object {
        fun init(project: Project): Settings {
            val defaultSettings = Settings(
                excludeTags = emptyList(),
                globalRules = Rules(
                    applyClickIgnore = true,
                    useEmptyStrings = true,
                    useNegativeNumbers = false,
                    testConditionals = true
                ),
                forNode = listOf(
                    SettingsNode(
                        nodeTag = "testTag",
                        rules = Rules(
                            applyClickIgnore = true,
                            useEmptyStrings = false,
                            useNegativeNumbers = false,
                            testConditionals = true
                        )
                    )
                )
            )

            ensureConfigFileExists(project, defaultSettings)
            val currentSettings = readConfigFile(project)
            return currentSettings ?: defaultSettings
        }

        private fun ensureConfigFileExists(project: Project, defaultSettings: Settings) {
            val projectBaseDir = project.guessProjectDir()
            val configFile = projectBaseDir?.findChild(Cons.CONFIG_FILE_NAME)

            if (configFile == null || !configFile.exists()) {
                val gson = GsonBuilder().setPrettyPrinting().create()
                val json = gson.toJson(defaultSettings)

                val file = File(projectBaseDir?.path, Cons.CONFIG_FILE_NAME)
                try {
                    file.writeText(json)
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)?.refresh(false, false)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        private fun readConfigFile(project: Project): Settings? {
            val projectBaseDir = project.guessProjectDir()
            val configFile = File(projectBaseDir?.path, Cons.CONFIG_FILE_NAME)

            if (configFile.exists()) {
                val gson = Gson()
                return configFile.reader().use { reader ->
                    gson.fromJson(reader, Settings::class.java)
                }
            }
            return null
        }
    }
}

data class Rules(
    val applyClickIgnore: Boolean,
    val useEmptyStrings: Boolean,
    val useNegativeNumbers: Boolean,
    val testConditionals: Boolean
)

data class SettingsNode(
    val nodeTag: String,
    val rules: Rules
)