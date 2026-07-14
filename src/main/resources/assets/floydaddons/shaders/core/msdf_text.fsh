#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#endif

uniform sampler2D Sampler0;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#endif
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

const float UNIT_RANGE = 4.0 / 1024.0;

float median3(vec3 v) {
    return max(min(v.r, v.g), min(max(v.r, v.g), v.b));
}

float screenPxRange() {
    return max(0.5 * dot(vec2(UNIT_RANGE), 1.0 / fwidth(texCoord0)), 1.0);
}

void main() {
    float sd = median3(texture(Sampler0, texCoord0).rgb);
    float opacity = clamp(screenPxRange() * (sd - 0.5) + 0.5, 0.0, 1.0);
    vec4 color = vertexColor * ColorModulator;
    color.a *= opacity;
    if (color.a < 0.1) {
        discard;
    }
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#else
    fragColor = color;
#endif
}
