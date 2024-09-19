package com.mercury.sqkon

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
    val child: TestObjectChild = TestObjectChild(),
    val list: List<TestObjectChild> = List(2) { TestObjectChild() },
)


@Serializable
data class TestObjectChild(
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
)

@JvmInline
@Serializable
value class TestValue(val test: String)
