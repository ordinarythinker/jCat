package com.ordinarythinker.jcat.generator

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import kotlin.random.Random
import kotlin.reflect.full.isSubclassOf

//import org.mockito.kotlin.mock
//import org.mockito.kotlin.whenever

data class Parameter(val name: String, val klazz: KClass<*>)

sealed class Params {
    data class SingleParam(val value: List<Any>) : Params()
    data class MultipleParams(val list: List<List<Any>>) : Params()
}

class Mocker {
    private val stringValues = listOf("", "some random string")
    private val intValues = listOf(-1, 0, Random.nextInt())
    private val booleanValues = listOf(true, false)

    fun generateMockData(parameterTypes: List<Parameter>): List<List<Any>> {
        val paramValues = parameterTypes.map { getPossibleValues(it.klazz) }
        return generateCombinations(paramValues)
    }

    private fun getPossibleValues(kClass: KClass<*>): Params {
        return when {
            kClass == String::class -> Params.SingleParam(stringValues)
            kClass == Int::class -> Params.SingleParam(intValues)
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

    fun extractParameters(function: KFunction<*>): List<Parameter> {
        return function.parameters.drop(1).map { parameter ->
            Parameter(parameter.name ?: "unknown", parameter.type.jvmErasure)
        }
    }

    fun generateMockCode(parameters: List<Parameter>, mockData: List<List<Any>>): List<String> {
        val codeList = mutableListOf<String>()

        mockData.forEachIndexed { index, params ->
            val code = StringBuilder()
            params.forEachIndexed { paramIndex, paramValue ->
                val parameter = parameters[paramIndex]
                val paramName = parameter.name
                val paramClass = parameter.klazz

                when {
                    paramClass.isData -> {
                        val mockName = "${paramName}Mock$index"
                        code.append("val $mockName = mock<${paramClass.simpleName}> {\n")
                        paramClass.primaryConstructor?.parameters?.forEach { constructorParam ->
                            val property = paramClass.declaredMemberProperties.first { it.name == constructorParam.name }
                            val propertyName = property.name
                            code.append("    whenever(it.$propertyName).thenReturn(params[$paramIndex] as ${property.returnType.jvmErasure.simpleName})\n")
                        }
                        code.append("}\n")
                    }
                    paramClass == String::class || paramClass == Int::class || paramClass == Boolean::class -> {
                        code.append("val ${paramName}Mock = params[$paramIndex] as ${paramClass.simpleName}\n")
                    }
                    paramClass.isSubclassOf(Collection::class) -> {
                        code.append("val ${paramName}Mock = params[$paramIndex] as ${paramClass.simpleName}\n")
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported type: ${paramClass.simpleName}")
                    }
                }
            }
            codeList.add(code.toString())
        }

        return codeList
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
