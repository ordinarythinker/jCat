package com.ordinarythinker.jcat.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.ordinarythinker.jcat.models.Parameter
import com.ordinarythinker.jcat.settings.Settings
import com.ordinarythinker.jcat.utils.generateRandomString
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
                parameters.mapIndexed { index, property ->
                    "${property.name} = ${list[index]}"
                }
                    .joinToString(", ")
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getPossibleValues(parameter: Parameter): List<Any> {
        val numberValues = getNumberValues()
        return when {
            parameter.klazz.name == "String" -> {
                getStringValues().map { "${parameter.name} = $it" }
            }
            parameter.klazz.name == "Int" -> {
                numberValues.map { "${parameter.name} = $it" }
            }
            parameter.klazz.name == "Double" -> {
                numberValues.map { it.toDouble() }.map { "${parameter.name} = $it" }
            }
            parameter.klazz.name == "Float" -> {
                numberValues.map { it.toFloat() }.map { "${parameter.name} = $it" }
            }
            parameter.klazz.name == "Long" -> {
                numberValues.map { it.toLong() }.map { "${parameter.name} = $it" }
            }
            parameter.klazz.name == "Short" -> {
                numberValues.map { it.toShort() }.map { "${parameter.name} = $it" }
            }
            parameter.klazz.name == "Boolean" -> {
                booleanValues.map { "${parameter.name} = $it" }
            }

            parameter.klazz.isEnum() -> {
                parameter.klazz.declarations.filterIsInstance<KtEnumEntry>().map { entry ->
                    "${parameter.klazz.name}.${entry.name}"
                }.map { "${parameter.name} = $it" }
            }

            parameter.klazz.isData() -> {
                parameter.klazz.fqName?.asString()?.let { imports.add(it) }

                val properties = parameter.klazz.primaryConstructorParameters.mapNotNull { parameter ->
                    val propertyName = parameter.name
                    val propertyType = parameter.typeReference
                    val bindingContext = propertyType?.analyze(BodyResolveMode.PARTIAL)
                    val kotlinType = bindingContext?.get(BindingContext.TYPE, propertyType)
                    val paramClass = kotlinType?.let { findClassByName(it) }

                    if (propertyName != null && paramClass != null) {
                        Parameter(name = propertyName, klazz = paramClass)
                    } else {
                        null
                    }
                }

                val propertyValues = properties.map { property ->
                    getPossibleValues(property)
                }

                val linearized = getAllCombinations(propertyValues)

                return linearized.map {
                    "${parameter.klazz.name}(${it.joinToString(", ")})"
                }
            }
            else -> {
                emptyList()
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

    private fun findClassByName(kotlinType: KotlinType): KtClass? {
        val classDescriptor = kotlinType.constructor.declarationDescriptor as? ClassDescriptor
        val fqName = classDescriptor?.fqNameSafe

        return if (fqName != null) {
            val psiClass = KotlinFullClassNameIndex.getInstance()
                .get(fqName.asString(), project, GlobalSearchScope.allScope(project))
                .firstOrNull() as? KtClassOrObject
            psiClass as? KtClass
        } else null
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
}
