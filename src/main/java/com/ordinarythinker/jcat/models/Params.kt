package com.ordinarythinker.jcat.models

sealed class Params {
    data class SingleParam(val value: List<Any>) : Params()
    data class MultipleParams(val list: List<List<Any>>) : Params()
}