package org.fossify.phone.models

data class LightContact(
    val id: Long,
    val name: String,
    val number: String,
    val photoUri: String?
)