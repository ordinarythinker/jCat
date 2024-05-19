package com.ordinarythinker.jcat.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.ordinarythinker.jcat.models.Parameter
import com.ordinarythinker.jcat.models.Params
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import kotlin.random.Random

class Mocker(
    private val project: Project
) {
    private val stringValues = listOf("", "some random string")
    private val numberValues = listOf(-1, 0, Random.nextInt())
    private val booleanValues = listOf(true, false)
    val imports: MutableList<String> = mutableListOf()

    fun generateMockData(parameterTypes: List<Parameter>): List<List<Any>> {
        val paramValues = parameterTypes.map { getPossibleValues(it.klazz) }
        return generateCombinations(paramValues)
    }

    private fun getPossibleValues(kClass: KtClass): Params {
        return when {
            kClass.name == "String" -> {
                Params.SingleParam(stringValues)
            }
            kClass.name == "Int" -> {
                Params.SingleParam(numberValues)
            }
            kClass.name == "Double" -> {
                Params.SingleParam(numberValues.map { it.toDouble() })
            }
            kClass.name == "Float" -> {
                Params.SingleParam(numberValues.map { it.toFloat() })
            }
            kClass.name == "Long" -> {
                Params.SingleParam(numberValues.map { it.toLong() })
            }
            kClass.name == "Short" -> {
                Params.SingleParam(numberValues.map { it.toShort() })
            }
            kClass.name == "Boolean" -> {
                Params.SingleParam(booleanValues)
            }

            kClass.isData() -> {
                kClass.fqName?.asString()?.let { imports.add(it) }

                val properties = kClass.primaryConstructorParameters.mapNotNull { parameter ->
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

                Params.MultipleParams(generateMockData(properties))
            }
            else -> {
                throw IllegalArgumentException("Unsupported type: ${kClass.name}")
            }
        }
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

    private fun generateCombinations(params: List<Params>): List<List<Any>> {
        if (params.isEmpty()) return listOf(emptyList())

        val firstParam = params.first()
        val restParams = params.drop(1)
        val restCombinations = generateCombinations(restParams)

        return when (firstParam) {
            is Params.SingleParam -> {
                firstParam.value.flatMap { value ->
                    restCombinations.map { combination ->
                        listOf(value) + combination
                    }
                }
            }
            is Params.MultipleParams -> {
                firstParam.list.flatMap { valueList ->
                    valueList.flatMap { value ->
                        restCombinations.map { combination ->
                            listOf(value) + combination
                        }
                    }
                }
            }
        }
    }
}
