package com.ordinarythinker.jcat

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class ProcessKtFileAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val file: VirtualFile? = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file != null && file.isValid) {
            val psiFile: PsiFile? = e.getData(CommonDataKeys.PSI_FILE)
            if (psiFile != null && psiFile.fileType == KotlinFileType.INSTANCE) {
                // Check if it's a Jetpack Compose function file
                if (containsComposableFunctions(psiFile)) {
                    // Perform your plugin task with the file
                    try {
                        runPluginTask(project, psiFile.virtualFile)
                    } catch (ex: Exception) {
                        // Handle exceptions
                        ex.printStackTrace()
                    }
                }
            }
        }
    }

    private fun containsComposableFunctions(psiFile: PsiFile): Boolean {
        if (psiFile !is KtFile) {
            return false
        }

        var containsComposable = false

        // Traverse the PSI tree
        psiFile.acceptChildren(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                println("current element text: ${element.text}")

                if (element is KtNamedFunction) {
                    // Check if the function is annotated with @Composable
                    if (isComposableFunction(element)) {
                        containsComposable = true
                        return
                    }
                }
                super.visitElement(element)
            }
        })

        return containsComposable
    }

    private fun isComposableFunction(function: KtNamedFunction): Boolean {
        val annotation = function.annotationEntries.find { it.isComposableAnnotation() }
        return annotation != null
    }

    private fun runPluginTask(project: Project, file: VirtualFile) {
        val psiManager = PsiManager.getInstance(project)
        val psiFile = psiManager.findFile(file)
        if (psiFile !is KtFile) return
        val ktFile : KtFile = psiFile

        val composableFunctions = ktFile.children.filterIsInstance<KtNamedFunction>()
            .filter { it.annotationEntries.any { entry -> entry.isComposableAnnotation() } }

        if (composableFunctions.isNotEmpty()) {
            val packageName = psiFile.packageFqName.asString()
            val testGenerator = TestGenerator(project)
            testGenerator.generateTestFile(composableFunctions, packageName)
        }
    }
}
