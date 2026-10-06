package com.example.planningbgt.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventFormValidatorTest {

    private val now = 1_000_000L
    private val future = now + 60_000L

    private fun validate(
        title: String = "Fiesta",
        description: String = "Plan de prueba",
        dateMillis: Long? = future,
        hasLocation: Boolean = true,
        capacityText: String = "20",
        category: String? = "fiesta"
    ) = EventFormValidator.validate(
        title, description, dateMillis, hasLocation, capacityText, category, nowMillis = now
    )

    @Test
    fun `formulario valido no tiene errores`() {
        assertFalse(validate().hasErrors)
    }

    @Test
    fun `titulo vacio o solo espacios es requerido`() {
        assertEquals(FieldError.REQUIRED, validate(title = "").title)
        assertEquals(FieldError.REQUIRED, validate(title = "   ").title)
    }

    @Test
    fun `titulo respeta el maximo de 80`() {
        assertNull(validate(title = "a".repeat(80)).title)
        assertEquals(FieldError.TOO_LONG, validate(title = "a".repeat(81)).title)
    }

    @Test
    fun `descripcion es requerida y maximo 500`() {
        assertEquals(FieldError.REQUIRED, validate(description = " ").description)
        assertNull(validate(description = "a".repeat(500)).description)
        assertEquals(FieldError.TOO_LONG, validate(description = "a".repeat(501)).description)
    }

    @Test
    fun `fecha es requerida y debe ser futura`() {
        assertEquals(FieldError.REQUIRED, validate(dateMillis = null).date)
        assertEquals(FieldError.DATE_IN_PAST, validate(dateMillis = now - 1).date)
        assertEquals(FieldError.DATE_IN_PAST, validate(dateMillis = now).date)
        assertNull(validate(dateMillis = now + 1).date)
    }

    @Test
    fun `ubicacion es requerida`() {
        assertEquals(FieldError.REQUIRED, validate(hasLocation = false).location)
    }

    @Test
    fun `capacidad vacia es requerida`() {
        assertEquals(FieldError.REQUIRED, validate(capacityText = "").capacity)
    }

    @Test
    fun `capacidad no numerica es invalida`() {
        assertEquals(FieldError.INVALID_NUMBER, validate(capacityText = "abc").capacity)
    }

    @Test
    fun `capacidad fuera de rango 1 a 1000`() {
        assertEquals(FieldError.OUT_OF_RANGE, validate(capacityText = "0").capacity)
        assertEquals(FieldError.OUT_OF_RANGE, validate(capacityText = "1001").capacity)
        assertNull(validate(capacityText = "1").capacity)
        assertNull(validate(capacityText = "1000").capacity)
        assertNull(validate(capacityText = " 50 ").capacity)
    }

    @Test
    fun `categoria es requerida y debe ser una conocida`() {
        assertEquals(FieldError.REQUIRED, validate(category = null).category)
        assertEquals(FieldError.REQUIRED, validate(category = "inventada").category)
        EventFormValidator.categories.forEach {
            assertNull(validate(category = it).category)
        }
    }

    @Test
    fun `hasErrors es true si falla cualquier campo`() {
        assertTrue(validate(capacityText = "0").hasErrors)
    }
}