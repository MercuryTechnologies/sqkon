package com.mercury.sqkon.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.descriptors.runtime.components.tryLoadClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalCompilerApi::class)
class SerialNameIrVisitorTest() {


    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()


    private lateinit var compilationDir: File
    private lateinit var resourcesDir: File


    @BeforeTest
    fun setup() {
        compilationDir = tempFolder.newFolder("compilationDir")
        resourcesDir = tempFolder.newFolder("resourcesDir")
    }


    @Test
    fun `generateIndexes basic`() {
        val result =
            compile(
                kotlin(
                    "simple.kt",
                    """
                    package test
                        
                    import kotlinx.serialization.Serializable

                    @Serializable
                    data class User(
                        val id: Int,
                        val name: String,
                    )
                    """,
                )
            )

        assertEquals(ExitCode.OK, result.exitCode)
        result.assertCompanionObject(
            "test.User",
            mapOf("id" to 0, "name" to 1)
        )
    }

    @Test
    fun `generateIndexes with Transient`() {
        val result =
            compile(
                kotlin(
                    "simple.kt",
                    """
                    package test
                        
                    import kotlinx.serialization.Serializable
                    import kotlinx.serialization.Transient

                    @Serializable
                    data class User(
                        val id: Int,
                        val name: String,
                        @Transient
                        val ignored: String,
                    )
                    """,
                )
            )

        assertEquals(ExitCode.OK, result.exitCode)
        result.assertCompanionObject(
            "test.User",
            mapOf("id" to 0, "name" to 1)
        )
    }

    @Test
    fun `generateIndexes with properties`() {
        val result =
            compile(
                kotlin(
                    "simple.kt",
                    """
                    package test
                        
                    import kotlinx.serialization.Serializable
                    import kotlinx.serialization.Transient

                    @Serializable
                    data class User(
                        val id: Int,
                        val name: String,
                        @Transient
                        val ignored: String,
                    ) {
                        val serializedName: String = name
                        val fullName: String get() = "'$'name '$'id"
                        
                    }
                    """,
                )
            )

        assertEquals(ExitCode.OK, result.exitCode)
        result.assertCompanionObject(
            "test.User",
            mapOf("id" to 0, "name" to 1, "serializedName" to 2)
        )
    }

    private fun JvmCompilationResult.assertCompanionObject(
        className: String, expectedIndexes: Map<String, Int>
    ) {
        val userClass = classLoader.tryLoadClass(className)
        assertNotNull(userClass)
        val companionClass = userClass.companionOrNull("Companion")
        assertNotNull(companionClass)
        val getPropertyIndicesMethod =
            companionClass::class.java.declaredMethods.find { it.name == "getPropertyIndices" }
        assertNotNull(getPropertyIndicesMethod)
        val propertyIndices =
            getPropertyIndicesMethod.invoke(companionClass, companionClass) as Map<String, Int>
        assertEquals(expectedIndexes, propertyIndices)
    }

    private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation {
        return KotlinCompilation().apply {
            workingDir = compilationDir
            compilerPluginRegistrars = listOf(SerialIndexComponentRegistrar())
            inheritClassPath = true
            sources = sourceFiles.asList()
            verbose = false
            // Necessary for K2 testing, even if useK2 itself isn't part of this test!
            kotlincArguments += listOf("-Xskip-prerelease-check", "-Xallow-unstable-dependencies")
        }
    }

    private fun compile(vararg sourceFiles: SourceFile): JvmCompilationResult {
        return prepareCompilation(*sourceFiles).compile()
    }

    private fun Class<*>.companionOrNull(companionName: String) =
        try {
            val companion = getDeclaredField(companionName)
            companion.isAccessible = true
            companion.get(null)
        } catch (e: Throwable) {
            null
        }

}

