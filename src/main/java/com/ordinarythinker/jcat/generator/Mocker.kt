package com.ordinarythinker.jcat.generator

import com.ordinarythinker.jcat.models.Parameter
import com.ordinarythinker.jcat.models.Params
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.jvmErasure
import kotlin.random.Random
import kotlin.reflect.full.isSubclassOf

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

    private fun getPossibleValues(kClass: KClass<*>): Params {
        return when {
            kClass == String::class -> Params.SingleParam(stringValues)
            kClass == Int::class -> Params.SingleParam(numberValues)
            kClass == Double::class -> Params.SingleParam(numberValues.map { it.toDouble() })
            kClass == Float::class -> Params.SingleParam(numberValues.map { it.toFloat() })
            kClass == Long::class -> Params.SingleParam(numberValues.map { it.toLong() })
            kClass == Short::class -> Params.SingleParam(numberValues.map { it.toShort() })
            kClass == Boolean::class -> Params.SingleParam(booleanValues)

            kClass.isSubclassOf(Collection::class) -> {
                val elementType = kClass.supertypes.first().arguments.first().type?.classifier as KClass<*>
                val elementValues = (getPossibleValues(elementType) as Params.SingleParam).value
                Params.MultipleParams(
                    listOf(
                        emptyList(),
                        List(5) { elementValues.random() }
                    )
                )
            }
            kClass.isData -> {
                kClass.qualifiedName?.let { imports.add(it) }

                val properties = kClass.declaredMemberProperties.map { it.returnType.jvmErasure }
                Params.MultipleParams(
                    generateMockData(properties.map {
                        Parameter(it.qualifiedName!!, it)
                    })
                )
            }
            else -> throw IllegalArgumentException("Unsupported type: $kClass")
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

// Example usage
/*
fun main() {
    data class User(val name: String, val age: Int)

    @Composable
    fun UserInfo(user: User, isEditingEnabled: Boolean, email: String) {
        // Your composable function implementation
    }

    val mocker = Mocker()
    val userInfoFunction = ::UserInfo
    val parameterList = mocker.extractParameters(userInfoFunction)

    val mockData = mocker.generateMockData(parameterList)
    val generatedCode = mocker.generateMockCode(parameterList, mockData)

    generatedCode.forEach { code ->
        println(code)
    }
}
*/
