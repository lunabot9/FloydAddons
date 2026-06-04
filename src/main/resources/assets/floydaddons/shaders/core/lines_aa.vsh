#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;       // segment direction (Position + Normal == the other endpoint)
in float LineWidth;   // 1.21.11: line width is a per-vertex attribute

noperspective out float edgePx;   // signed cross-line distance from the centerline, in TRUE pixels
out float vHalfPx;                 // solid half-width in pixels (constant per segment)
out vec4 vColor;

const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);
const mat4 VIEW_SCALE = mat4(
    VIEW_SHRINK, 0.0, 0.0, 0.0,
    0.0, VIEW_SHRINK, 0.0, 0.0,
    0.0, 0.0, VIEW_SHRINK, 0.0,
    0.0, 0.0, 0.0, 1.0
);

const float NEAR_W = 0.05;

void main() {
    vec4 cThis  = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position, 1.0);
    vec4 cOther = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position + Normal, 1.0);

    // Near-plane clamp: pull a behind-camera endpoint up to the near plane along the segment so a tracer
    // to a target behind you runs off the screen edge instead of exploding through w<=0 ("cuts at body").
    if (cThis.w < NEAR_W && cOther.w >= NEAR_W) {
        cThis = mix(cThis, cOther, (NEAR_W - cThis.w) / (cOther.w - cThis.w));
    } else if (cOther.w < NEAR_W && cThis.w >= NEAR_W) {
        cOther = mix(cOther, cThis, (NEAR_W - cOther.w) / (cThis.w - cOther.w));
    }

    vec2 ndcThis  = cThis.xy / cThis.w;
    vec2 ndcOther = cOther.xy / cOther.w;

    vec2 deltaPx = (ndcOther - ndcThis) * ScreenSize;
    float len = length(deltaPx);
    vec2 dir = (len > 1e-3) ? (deltaPx / len) : vec2(1.0, 0.0);   // guard degenerate (endpoints coincident)
    vec2 perp = vec2(-dir.y, dir.x);

    float halfPx = max(LineWidth, 1.5);   // half-width in px, floored so there is always a solid core
    float padPx  = halfPx + 2.0;          // inflate so the coverage feather is never clipped by the quad edge

    // No winding flip (it splits lines into two backface-culled groups). Pure rotation => uniform winding.
    float side = (gl_VertexID % 2 == 0) ? 1.0 : -1.0;
    vec2 offsetNdc = (side * perp * padPx) * 2.0 / ScreenSize;   // px -> NDC
    cThis.xy += offsetNdc * cThis.w;                              // back into clip space by * w

    gl_Position = cThis;
    edgePx = side * padPx;
    vHalfPx = halfPx;
    vColor = Color;
}
