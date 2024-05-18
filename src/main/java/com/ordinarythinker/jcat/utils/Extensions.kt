package com.ordinarythinker.jcat.utils

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import java.util.*
import kotlin.reflect.KClass

fun KtAnnotationEntry?.isComposableAnnotation(): Boolean {
    // Customize this as per your project's imports and annotation class
    return this?.shortName?.asString() == "Composable"
}

fun PsiFile.isKtFile(): Boolean {
    return this.javaClass.kotlin.qualifiedName == "org.jetbrains.kotlin.psi.KtFile"
}

fun KtClass.toKClass(): KClass<*> {
    val fqName = this.fqName?.asString()
    val loadedClass: Class<*> = try {
        Class.forName(fqName)
    } catch (e: ClassNotFoundException) {
        Any::class.java
    }
    return loadedClass.kotlin
}

fun generateRandomString(length: Int = Random().nextInt(100)): String {
    val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    return (1..length)
        .map { Random().nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("")
}