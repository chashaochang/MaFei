package com.kyant.backdrop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import org.intellij.lang.annotations.Language

expect fun RuntimeShader(@Language("AGSL") shaderString: String): RuntimeShader

expect fun RuntimeShader.asComposeShader(): Shader

/**
 * Prepares a reusable immutable program when the platform exposes one. Does not
 * instantiate a mutable uniform builder, draw, or warm the final GPU pipeline.
 * Android returns false: its public RuntimeShader API cannot separate the program.
 */
internal expect fun prewarmRuntimeShaderProgram(@Language("AGSL") shaderString: String): Boolean

interface RuntimeShader {

    fun setFloatUniform(name: String, value: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)
    fun setFloatUniform(name: String, values: FloatArray)

    fun setIntUniform(name: String, value: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int)
    fun setIntUniform(name: String, values: IntArray)

    fun setColorUniform(name: String, color: Color)
}
