package com.mercury.sqkon

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.internal.jsonCachedSerialNames
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class DescriptorTest {

    @Test
    fun getDescriptorInfoOfInlineClass() {
        val d = getDescriptor<TestValue>()
        assertTrue(d.isInline)
    }

    @Test
    fun getDescriptorInfoOfLis() {
        val d = getDescriptor<List<TestObjectChild>>()
        assertEquals(StructureKind.LIST, d.kind)
        println(d.elementDescriptors.forEach { println(it) })
        val typeD = d.elementDescriptors.first()
        println(typeD)
        assertEquals(StructureKind.CLASS, typeD.kind)
    }

    private inline fun <reified T : Any> getDescriptor(): SerialDescriptor {
        return serializer<T>().descriptor
    }

}