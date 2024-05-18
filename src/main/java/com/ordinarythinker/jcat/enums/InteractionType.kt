package com.ordinarythinker.jcat.enums

import com.ordinarythinker.jcat.utils.Cons.VALID_EMAIL
import com.ordinarythinker.jcat.utils.generateRandomString
import java.util.Random

sealed class InteractionType {
    data object Visibility : InteractionType()
    sealed class Clickable : InteractionType() {
        data object NoClick : Clickable()
        data object PerformClick : Clickable()
    }

    sealed class Input : InteractionType() {
        data object NoInput : Input()
        data class NumberInput(
            val value: Int = Random().nextInt()
        ) : Input()

        data class ValidEmailInput(
            val value: String = VALID_EMAIL
        ) : Input()

        data class RandomStringInput(
            val value: String = generateRandomString()
        ) : Input()
    }
}

sealed class Interaction {
    data class Single(
        val interactionType: InteractionType
    ) : Interaction()

    data class Multiple(
        val interactions: List<InteractionType>
    ) : Interaction()
}