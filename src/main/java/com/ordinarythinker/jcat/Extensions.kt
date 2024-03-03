package com.ordinarythinker.jcat

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtAnnotationEntry

fun KtAnnotationEntry?.isComposableAnnotation(): Boolean {
    // Customize this as per your project's imports and annotation class
    return this?.shortName?.asString() == "Composable"
}

fun PsiFile.isKtFile() : Boolean {
    return this.javaClass.kotlin.qualifiedName == "org.jetbrains.kotlin.psi.KtFile"
}