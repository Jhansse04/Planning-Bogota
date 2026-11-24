package com.example.planningbgt.util

import com.example.planningbgt.model.User

/** Funciones que dependen del plan. Para proteger una nueva, se agrega aquí. */
enum class PlanFeature { ASSOCIATED_ACCOUNTS }

/**
 * Gating por plan en un solo lugar: lo usan la UI, los repositorios y los tests.
 * En el servidor, su equivalente es isPro() de firebase/firestore.rules.
 */
object PlanPolicy {
    const val PLAN_PRO = "pro"

    private val requiredPlans = mapOf(
        PlanFeature.ASSOCIATED_ACCOUNTS to setOf(PLAN_PRO)
    )

    fun isAllowed(feature: PlanFeature, accountType: String?): Boolean =
        accountType in requiredPlans.getValue(feature)

    fun isAllowed(feature: PlanFeature, user: User?): Boolean =
        isAllowed(feature, user?.accountType)
}
