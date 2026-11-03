package com.example.planningbgt.util

enum class FieldError {
    REQUIRED, TOO_LONG, INVALID_NUMBER, CAPACITY_TOO_LOW, CAPACITY_TOO_HIGH, DATE_IN_PAST,INVALID_URL
}

data class EventFormErrors(
    val title: FieldError? = null,
    val imageUrl: FieldError? = null,
    val description: FieldError? = null,
    val date: FieldError? = null,
    val location: FieldError? = null,
    val capacity: FieldError? = null,
    val category: FieldError? = null
) {
    val hasErrors: Boolean
        get() = listOf(title, description, date, location, capacity, category,imageUrl).any { it != null }
}


object EventFormValidator {
    const val TITLE_MAX_LENGTH = 80
    const val DESCRIPTION_MAX_LENGTH = 500
    const val CAPACITY_MIN = 2
    const val CAPACITY_MAX = 30            // eventos privados
    const val CAPACITY_MAX_PUBLIC = 500    // eventos públicos
    const val IMAGE_URL_MAX_LENGTH = 500

    const val TYPE_PRIVATE = "private"
    const val TYPE_PUBLIC = "public"

    val categories = listOf(
        "fiesta", "deporte", "cultural", "academico", "gastronomia", "musica", "otro"
    )

    /** Capacidad máxima según el tipo de evento. Cualquier valor distinto de "public" se trata como privado. */
    fun maxCapacityFor(eventType: String): Int =
        if (eventType == TYPE_PUBLIC) CAPACITY_MAX_PUBLIC else CAPACITY_MAX

    /**
     * Regla de negocio: un evento privado puede pasar a público, pero un público
     * no puede volver a ser privado. (Se usará cuando exista la edición de eventos.)
     */
    fun canChangeEventType(from: String, to: String): Boolean = when {
        from == to -> true
        from == TYPE_PRIVATE && to == TYPE_PUBLIC -> true
        else -> false
    }
        /** La foto es opcional y solo existe en eventos públicos. Debe ser un enlace https válido. */
    private fun imageUrlError(imageUrl: String, eventType: String): FieldError? {
        val url = imageUrl.trim()
        if (eventType != TYPE_PUBLIC || url.isEmpty()) return null
        if (url.length > IMAGE_URL_MAX_LENGTH) return FieldError.TOO_LONG
        val isValid = url.startsWith("https://") &&
            url.none { it.isWhitespace() } &&
            runCatching { java.net.URI(url).host }.getOrNull()?.isNotBlank() == true
        return if (isValid) null else FieldError.INVALID_URL
    }

    fun validate(
        title: String,
        description: String,
        dateMillis: Long?,
        hasLocation: Boolean,
        capacityText: String,
        category: String?,
        nowMillis: Long = System.currentTimeMillis(),
        eventType: String = TYPE_PRIVATE,
         imageUrl: String = ""

    ): EventFormErrors {
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        val trimmedCapacity = capacityText.trim()
        val maxCapacity = maxCapacityFor(eventType)

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
                    value < CAPACITY_MIN -> FieldError.CAPACITY_TOO_LOW
                    value > maxCapacity -> FieldError.CAPACITY_TOO_HIGH
                    else -> null
                }
            },
            category = if (category != null && category in categories) null else FieldError.REQUIRED,imageUrl = imageUrlError(imageUrl, eventType)
        )
    }
}