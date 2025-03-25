package com.mercury.sqkon.compiler

import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.JvmDefaultMode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
class SerialNameIrVisitorTest {


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
    fun generateIndexesSimple() {
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
}

