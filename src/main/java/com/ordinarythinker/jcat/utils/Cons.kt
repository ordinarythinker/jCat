package com.ordinarythinker.jcat.utils

object Cons {
    const val CONFIG_FILE_NAME = "test-generator.config"
    const val COMPOSABLE_QUALIFIED_ANNOTATION = "androidx.compose.runtime.Composable"
    const val TEXT_FIELD = "TextField"
    const val OUTLINED_TEXT_FIELD = "OutlinedTextField"
    const val BASIC_TEXT_FIELD = "OutlinedTextField"
    val textFields = listOf(TEXT_FIELD, OUTLINED_TEXT_FIELD, BASIC_TEXT_FIELD)

    const val FUNCTION_BUTTON = "Button"
    const val FUNCTION_ICON_BUTTON = "IconButton"
    const val FUNCTION_CHECKBOX = "Checkbox"
    const val FUNCTION_RADIO_BUTTON = "RadioButton"
    const val FUNCTION_SWITCH = "Switch"
    const val FUNCTION_SLIDER = "Slider"
    const val FUNCTION_DROPDOWN_MENU = "DropdownMenu"

    val clickables = listOf(FUNCTION_BUTTON, FUNCTION_ICON_BUTTON, FUNCTION_CHECKBOX, FUNCTION_RADIO_BUTTON,
        FUNCTION_SWITCH, FUNCTION_SLIDER, FUNCTION_DROPDOWN_MENU)

    const val FUNCTION_TEXT = "Text"
    const val FUNCTION_IMAGE = "Image"
    val visibles = listOf(FUNCTION_TEXT, FUNCTION_IMAGE)

    const val emptyString = ""
    const val VALID_EMAIL = "example@email.com"
}