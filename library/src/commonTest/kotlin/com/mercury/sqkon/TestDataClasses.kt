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
    val attributes: List<String>? = (1..10).map { it.toString() },
    val sealed: TestSealed = TestSealed.Impl2("value"),
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


@Serializable
sealed interface TestSealed {
    @Serializable
    @SerialName("Impl")
    data class Impl(val boolean: Boolean) : TestSealed

    @JvmInline
    @Serializable
    @SerialName("Impl2")
    value class Impl2(val value: String) : TestSealed

}

@Serializable
sealed interface BaseSealed {

    val id: String

    @JvmInline
    @Serializable
    @SerialName("TypeOne")
    value class TypeOne(
        val data: TypeOneData
    ) : BaseSealed {
        override val id: String get() = data.key
    }

    @Serializable
    @SerialName("TypeTwo")
    data class TypeTwo(
        val data: TypeTwoData
    ) : BaseSealed {
        override val id: String get() = data.key
    }

}

@Serializable
data class TypeOneData(
    val key: String,
    val value: String
)

@Serializable
data class TypeTwoData(
    val key: String,
    val otherValue: Int
)
