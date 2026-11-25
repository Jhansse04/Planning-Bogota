package com.example.planningbgt.model

fun String.getTypeLabel(): String = when (this) {
    "public" -> "Público"
    "private" -> "Privado"
    "paid_promotion" -> "Promoción"
    else -> this
}

object EventFiltersData {
    val categories = listOf(
        "Fiesta", "Concierto", "Deportivo", "Cultural",
        "Gastronómico", "Educativo", "Familiar", "Otro"
    )
    val eventTypes = listOf("public", "private", "paid_promotion")
}
