package com.mercury.sqkon

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class TestObject(
    val id: @Contextual Uuid = Uuid.random(),
    val name: String = "Name ${Random.nextInt(10000000)}",
    val value: Int = Random.nextInt(1000000000),
    val description: String = Random.nextInt(100).toString()
)
