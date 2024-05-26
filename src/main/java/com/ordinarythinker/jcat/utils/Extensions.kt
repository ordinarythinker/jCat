package com.ordinarythinker.jcat.utils

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import java.util.*
import kotlin.reflect.KClass

fun KtAnnotationEntry?.isComposableAnnotation(): Boolean {
    // Customize this as per your project's imports and annotation class
    return this?.shortName?.asString() == "Composable"
}

fun PsiFile.isKtFile(): Boolean {
    return this.javaClass.kotlin.qualifiedName == "org.jetbrains.kotlin.psi.KtFile"
}

fun generateRandomString(length: Int = Random().nextInt(100)): String {
    val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    return (1..length)
        .map { Random().nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("")
}

fun KtClass.isString(): Boolean {
    return this.name == "String"
}

fun KtClass.isInt(): Boolean {
    return this.name == "Int"
}

fun KtClass.isBoolean(): Boolean {
    return this.name == "Boolean"
}

fun KtClass.isCollection(): Boolean {
    return this.superTypeListEntries.any { it.text.startsWith("kotlin.collections.Collection<") }
}

fun KtClass.getProperties(): List<KtProperty> {
    return this.declarations.filterIsInstance<KtProperty>()
}

fun <T> getAllCombinations(lists: List<List<T>>): List<List<T>> {
    if (lists.isEmpty()) {
        return listOf(emptyList())
    }

    val head = lists.first()
    val tail = lists.drop(1)
    val combinations = getAllCombinations(tail)

    val result = mutableListOf<List<T>>()
    for (element in head) {
        for (combination in combinations) {
            val newCombination = mutableListOf(element)
            newCombination.addAll(combination)
            result.add(newCombination)
        }
    }

    return result
}