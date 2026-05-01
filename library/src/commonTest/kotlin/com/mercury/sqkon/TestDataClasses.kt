package com.mercury.sqkon

import kotlin.time.Clock
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
    val testEnum: TestEnum? = null,
)


@Serializable
data class TestObjectChild(
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val subList: List<String> = listOf("1", "2", "3"),
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

enum class TestEnum {
    FIRST,
    SECOND,

    @SerialName("unknown")
    LAST;
}

@Serializable
sealed interface SealedTimed {
    val id: String

    @Serializable
    @SerialName("Active")
    data class Active(
        override val id: String,
        val activatedAt: Long,
    ) : SealedTimed

    @Serializable
    @SerialName("Pending")
    data class Pending(
        override val id: String,
        val requestedAt: Long,
    ) : SealedTimed
}

@Serializable
sealed interface Order {
    val id: String

    @Serializable
    @SerialName("Active")
    data class Active(
        override val id: String,
        val dueAt: Long,
        val priority: Int,
    ) : Order

    @Serializable
    @SerialName("Pending")
    data class Pending(
        override val id: String,
        val reviewedAt: Long?,
        val escalated: Boolean = false,
    ) : Order

    @Serializable
    @SerialName("Cancelled")
    data class Cancelled(
        override val id: String,
        val reason: String,
    ) : Order
}

@Serializable
enum class ShipmentStatus { KEPT, RETURNED, IN_TRANSIT }

@Serializable
data class Shipment(
    val id: String,
    val status: ShipmentStatus,
    val trackerId: String? = null,
    val returnedAt: Long? = null,
    val flagged: Boolean = false,
)
