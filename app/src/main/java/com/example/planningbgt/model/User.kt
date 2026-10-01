package com.example.planningbgt.model

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profilePictureUrl: String = "",
    val privacyLevel: String = "public", // public, friends, private
    val accountType: String = "normal", // normal, pro, admin
    val createdAt: Timestamp = Timestamp.now()
)
