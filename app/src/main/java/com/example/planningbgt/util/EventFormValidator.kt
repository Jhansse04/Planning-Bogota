package com.example.planningbgt.util

enum class FieldError { REQUIRED, TOO_LONG, INVALID_NUMBER, OUT_OF_RANGE, DATE_IN_PAST }

data class EventFormErrors(
    val title: FieldError? = null,
    val description: FieldError? = null,
    val date: FieldError? = null,
    val location: FieldError? = null,
    val capacity: FieldError? = null,
    val category: FieldError? = null
) {
    val hasErrors: Boolean
        get() = listOf(title, description, date, location, capacity, category).any { it != null }
}


object EventFormValidator {
    const val TITLE_MAX_LENGTH = 80
    const val DESCRIPTION_MAX_LENGTH = 500
    const val CAPACITY_MIN = 1
    const val CAPACITY_MAX = 1000

    val categories = listOf(
        "fiesta", "deporte", "cultural", "academico", "gastronomia", "musica", "otro"
    )

    fun validate(
        title: String,
        description: String,
        dateMillis: Long?,
        hasLocation: Boolean,
        capacityText: String,
        category: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): EventFormErrors {
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        val trimmedCapacity = capacityText.trim()

        return EventFormErrors(
            title = when {
                trimmedTitle.isEmpty() -> FieldError.REQUIRED
                trimmedTitle.length > TITLE_MAX_LENGTH -> FieldError.TOO_LONG
                else -> null
            },
            description = when {
                trimmedDescription.isEmpty() -> FieldError.REQUIRED
                trimmedDescription.length > DESCRIPTION_MAX_LENGTH -> FieldError.TOO_LONG
                else -> null
            },
            date = when {
                dateMillis == null -> FieldError.REQUIRED
                dateMillis <= nowMillis -> FieldError.DATE_IN_PAST
                else -> null
            },
            location = if (hasLocation) null else FieldError.REQUIRED,
            capacity = if (trimmedCapacity.isEmpty()) {
                FieldError.REQUIRED
            } else {
                val value = trimmedCapacity.toIntOrNull()
                when {
                    value == null -> FieldError.INVALID_NUMBER
                    value < CAPACITY_MIN || value > CAPACITY_MAX -> FieldError.OUT_OF_RANGE
                    else -> null
                }
            },
            category = if (category != null && category in categories) null else FieldError.REQUIRED
        )
    }
}