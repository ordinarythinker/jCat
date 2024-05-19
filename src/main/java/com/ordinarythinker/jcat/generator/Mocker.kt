package com.ordinarythinker.jcat.generator

import com.ordinarythinker.jcat.models.Parameter
import com.ordinarythinker.jcat.models.Params
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtUserType
import kotlin.random.Random

class Mocker {
    private val stringValues = listOf("", "some random string")
    private val numberValues = listOf(-1, 0, Random.nextInt())
    private val booleanValues = listOf(true, false)
    val imports: MutableList<String> = mutableListOf()

    fun generateMockData(parameterTypes: List<Parameter>): List<List<Any>> {
        imports.clear()
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

                val properties = kClass.getProperties()
                Params.MultipleParams(
                    generateMockData(properties.map {
                        Parameter(it.name ?: "", it.typeReference?.let { typeRef ->
                            (typeRef.typeElement as? KtUserType)?.referencedName?.let { typeName ->
                                kClass.containingKtFile.classes.firstOrNull { it.name == typeName }
                            } as KtClass
                        } ?: throw IllegalArgumentException("Parameter type reference not found"))
                    })
                )
            }
            else -> {
                throw IllegalArgumentException("Unsupported type: ${kClass.name}")
            }
        }
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
