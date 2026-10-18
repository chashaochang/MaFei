package cn.mafei.cmp.common.glass.internal

// Shared by rendering and CPU program warmup; keep the optical shader source unchanged.
internal const val InteractiveHighlightShaderString = """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""

internal val VerticalProgressiveBlurShaderString = """
                    uniform shader content;

                    uniform float2 size;
                    layout(color) uniform half4 tint;
                    uniform float tintIntensity;
                    uniform float gradientStartFraction;
                    uniform float opacity;

                    half4 main(float2 coord) {
                        float alpha = (1.0 - smoothstep(
                            size.y * gradientStartFraction,
                            size.y,
                            coord.y
                        )) * opacity;
                        return mix(content.eval(coord) * alpha, tint * alpha, tintIntensity);
                    }
                """.trimIndent()
