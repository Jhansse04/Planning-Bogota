package com.example.planningbgt.model

import com.google.firebase.Timestamp

data class Friendship(
    val friendshipId: String = "",
    val user1Id: String = "",
    val user2Id: String = "",
    val status: String = "pending", // pending, accepted, blocked
    val createdAt: Timestamp = Timestamp.now()
)
