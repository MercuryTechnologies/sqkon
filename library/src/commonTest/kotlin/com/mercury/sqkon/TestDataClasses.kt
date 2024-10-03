package com.mercury.sqkon

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class TestObject(
    val id: String = Uuid.random().toString(),
    val name: String = "Name ${Uuid.random()}",
    val nullable: String? = "Name ${Uuid.random()}",
    val value: Int = Random.nextInt(Int.MAX_VALUE),
    val description: String = "Description ${Uuid.random()}",
    val testValue: TestValue = TestValue(Uuid.random().toString()),
    @SerialName("different_name")
    val serialName: String? = null,
    val child: TestObjectChild = TestObjectChild(),
    val list: List<TestObjectChild> = List(2) { TestObjectChild() },
    val attributes: List<String>? = (1..10).map { it.toString() }
)


@Serializable
data class TestObjectChild(
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
)

@Serializable
data class UnSerializable(
    val differentField: String
)

@JvmInline
@Serializable
value class TestValue(val test: String)
