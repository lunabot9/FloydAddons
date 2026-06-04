#version 330

#moj_import <minecraft:dynamictransforms.glsl>

noperspective in float edgePx;
in float vHalfPx;
in vec4 vColor;

out vec4 fragColor;

void main() {
    vec4 color = vColor * ColorModulator;

    float d = abs(edgePx);   // pixel distance from the centerline
    // Euclidean gradient (not fwidth's Manhattan sum) -> ~1px AA band at EVERY angle, 45deg included.
    float aa = max(length(vec2(dFdx(d), dFdy(d))), 0.5);
    float cov = clamp(1.0 - smoothstep(vHalfPx - aa, vHalfPx, d), 0.0, 1.0);

    float a = color.a * cov;
    if (a <= 0.0) discard;
    // Premultiplied output (paired with the premultiplied blend) so crossings/overlaps don't double-darken.
    fragColor = vec4(color.rgb * a, a);
}
