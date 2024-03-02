package com.ordinarythinker.jcat

import org.jetbrains.kotlin.psi.KtAnnotationEntry

fun KtAnnotationEntry?.isComposableAnnotation(): Boolean {
    // Customize this as per your project's imports and annotation class
    return this?.shortName?.asString() == "Composable"
}