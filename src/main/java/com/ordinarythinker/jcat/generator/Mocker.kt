package com.ordinarythinker.jcat.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.ordinarythinker.jcat.enums.ParameterType
import com.ordinarythinker.jcat.models.Parameter
import com.ordinarythinker.jcat.settings.Settings
import com.ordinarythinker.jcat.utils.generateRandomString
import com.ordinarythinker.jcat.utils.getAllCombinations
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import kotlin.random.Random

class Mocker(
    private val project: Project
) {
    private val settings: Settings = Settings.init(project)
    private val booleanValues = listOf(true, false)
    val imports: MutableList<String> = mutableListOf()

    fun generateMockData(parameters: List<Parameter>): List<String> {
        val paramValues = parameters.map { getPossibleValues(it) }
        val linearized = getAllCombinations(paramValues)

        return linearized.mapNotNull { list ->
            try {
                List(parameters.size) { index ->
                    "${list[index]}"
                }
                    .joinToString(",\n")
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getPossibleValues(parameter: Parameter): List<Any> {
        val numberValues = getNumberValues()
        return when (parameter.type) {
            ParameterType.Function -> listOf("${parameter.name} = {}")
            is ParameterType.Type -> {
                val klazz = parameter.type.clazz
                when {
                    klazz.name == "String" -> {
                        getStringValues().map { "${parameter.name} = $it" }
                    }
                    klazz.name == "Int" -> {
                        numberValues.map { "${parameter.name} = $it" }
                    }
                    klazz.name == "Double" -> {
                        numberValues.map { it.toDouble() }.map { "${parameter.name} = $it" }
                    }
                    klazz.name == "Float" -> {
                        numberValues.map { it.toFloat() }.map { "${parameter.name} = $it" }
                    }
                    klazz.name == "Long" -> {
                        numberValues.map { it.toLong() }.map { "${parameter.name} = $it" }
                    }
                    klazz.name == "Short" -> {
                        numberValues.map { it.toShort() }.map { "${parameter.name} = $it" }
                    }
                    klazz.name == "Boolean" -> {
                        booleanValues.map { "${parameter.name} = $it" }
                    }

                    klazz.isEnum() -> {
                        klazz.declarations.filterIsInstance<KtEnumEntry>().map { entry ->
                            "${klazz.name}.${entry.name}"
                        }.map { "${parameter.name} = $it" }
                    }

                    klazz.isData() -> {
                        klazz.fqName?.asString()?.let { imports.add(it) }

                        val properties = klazz.primaryConstructorParameters.mapNotNull { param ->
                            val propertyName = param.name
                            val propertyType = param.typeReference
                            val bindingContext = propertyType?.analyze(BodyResolveMode.PARTIAL)
                            val kotlinType = bindingContext?.get(BindingContext.TYPE, propertyType)
                            val paramClass = kotlinType?.let { findClassByName(it) }

                            if (propertyName != null && paramClass != null) {
                                Parameter(name = propertyName, type = paramClass)
                            } else {
                                null
                            }
                        }

                        val propertyValues = properties.map { property ->
                            getPossibleValues(property)
                        }

                        val linearized = getAllCombinations(propertyValues)

                        return linearized.map {
                            "${parameter.name} = ${klazz.name}(\n${it.joinToString(",\n")}\n)"
                        }
                    }
                    else -> {
                        emptyList()
                    }
                }
            }
        }
    }

    private fun getStringValues(): List<String> {
        val stringValues = mutableListOf(
            "\"${generateRandomString(20)}\""
        )

        if (settings.globalRules.useEmptyStrings) {
            stringValues.add("\"\"")
        }
        return stringValues
    }

    private fun getNumberValues(): List<Int> {
        val numberValues = mutableListOf(Random.nextInt(100))

        if (settings.globalRules.useNegativeNumbers) {
            numberValues.add(-1)
        }
        return numberValues
    }

    private fun findClassByName(kotlinType: KotlinType): ParameterType? {
        val classDescriptor = kotlinType.constructor.declarationDescriptor as? ClassDescriptor
        val fqName = classDescriptor?.fqNameSafe

        return if (fqName != null) {
            if (fqName.asString().startsWith("kotlin.Function")) {
                return ParameterType.Function
            }

            val psiClass = KotlinFullClassNameIndex.getInstance()
                .get(fqName.asString(), project, GlobalSearchScope.allScope(project))
                .firstOrNull() as? KtClassOrObject
            if (psiClass != null) {
                ParameterType.Type(psiClass as KtClass)
            } else null
        } else null
    }
}
