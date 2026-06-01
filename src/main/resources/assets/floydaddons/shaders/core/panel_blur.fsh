#version 150

// Per-panel frosted blur: samples the main framebuffer (Sampler0) behind the panel and box/gaussian
// blurs it, clipped to the same rounded-rect SDF the round_rect shader uses. Output alpha is the
// rounded mask, so the existing translucent panel fill + border composite cleanly on top.
layout(std140) uniform u {
    vec4 u_Rect;     // cx, cy, width, height   (local panel geometry, framebuffer px)
    vec4 u_Radii;    // topLeft, topRight, bottomRight, bottomLeft
    vec4 u_Screen;   // screenW, screenH, panelOriginX, panelOriginY   (framebuffer px)
    vec4 u_Blur;     // radius(px), kernelType(0=gaussian,1=box), unused, unused
};

uniform sampler2D Sampler0;

in vec2 f_Position;
in vec4 f_Color;
out vec4 fragColor;

float cornerRadius(vec2 p, vec4 r) {
    float sx = step(0.0, p.x);
    float sy = step(0.0, p.y);
    float top = mix(r.x, r.y, sx);
    float bottom = mix(r.w, r.z, sx);
    return mix(top, bottom, sy);
}

float roundedRectSDF(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 halfSize = u_Rect.zw * 0.5;
    vec2 p = f_Position - u_Rect.xy;

    float r = clamp(cornerRadius(p, u_Radii), 0.0, min(halfSize.x, halfSize.y));
    float d = roundedRectSDF(p, halfSize, r);
    float aa = max(fwidth(d), 0.75);
    float mask = 1.0 - smoothstep(0.0, aa, d);
    if (mask <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    // Screen-space UV of this fragment into the full-screen main framebuffer. The render target is
    // bottom-up, so flip Y relative to the top-down GUI coordinate space.
    vec2 screenPos = u_Screen.zw + f_Position;
    vec2 uv = screenPos / u_Screen.xy;
    uv.y = 1.0 - uv.y;

    vec2 texel = 1.0 / u_Screen.xy;
    float radius = max(u_Blur.x, 0.0);
    bool box = u_Blur.y > 0.5;
    float sigma = max(radius * 0.5, 0.0001);

    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    // Bounded 2D kernel sampled with a step of 2 (relying on bilinear filtering) to keep the sample
    // count reasonable. Separable two-pass is a perf follow-up.
    for (float dx = -radius; dx <= radius; dx += 2.0) {
        for (float dy = -radius; dy <= radius; dy += 2.0) {
            float wgt = box ? 1.0 : exp(-(dx * dx + dy * dy) / (2.0 * sigma * sigma));
            acc += texture(Sampler0, uv + vec2(dx, dy) * texel).rgb * wgt;
            wsum += wgt;
        }
    }

    vec3 col = wsum > 0.0 ? acc / wsum : texture(Sampler0, uv).rgb;
    fragColor = vec4(col, mask);
}
