#version 330 core

uniform float u_time;
uniform float u_speed;
uniform vec2 u_resolution;
uniform vec3 u_skyTopColor;
uniform vec3 u_skyHorizonColor;
uniform vec3 u_grassPrimaryColor;
uniform vec3 u_grassSecondaryColor;
uniform vec3 u_sunColor;
uniform vec3 u_fogColor;
uniform vec4 u_postFx; // contrast, saturation, brightness, vignette

out vec4 outColor;

float PI = 4.0 * atan(1.0);
vec3 sunLight = normalize(vec3(0.35, 0.2, 0.3));

float Hash(float n) { return fract(sin(n) * 43758.5453123); }
float Hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

float Noise(in vec2 x) {
    vec2 p = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float n = p.x + p.y * 57.0;
    return mix(mix(Hash(n + 0.0), Hash(n + 1.0), f.x),
               mix(Hash(n + 57.0), Hash(n + 58.0), f.x), f.y);
}

vec2 Voronoi(in vec2 x) {
    vec2 p = floor(x), f = fract(x);
    float res = 100.0, id = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 b = vec2(float(i), float(j));
            vec2 r = b - f + Hash(p + b);
            float d = dot(r, r);
            if (d < res) {
                res = d;
                id = Hash(p + b);
            }
        }
    }
    return vec2(max(0.4 - sqrt(res), 0.0), id);
}

float FractalNoise(in vec2 xy) {
    float w = 0.7, f = 0.0;
    for (int i = 0; i < 3; i++) {
        f += Noise(xy) * w;
        w *= 0.6;
        xy *= 2.0;
    }
    return f;
}

vec2 Terrain(in vec2 p) {
    vec2 pos = p * 0.003;
    float w = 50.0, f = 0.0;
    for (int i = 0; i < 3; i++) {
        f += Noise(pos) * w;
        w *= 0.62;
        pos *= 2.5;
    }
    return vec2(f, 0.0);
}

float RippleBand(in vec2 xz, in vec2 dir, in float freq, in float speed, in float amp, in float phase) {
    vec2 d = normalize(dir);
    float along = dot(xz, d) * freq;
    float warp = FractalNoise(xz * (freq * 0.085) + vec2(phase, -phase)) * 2.2;
    return sin(along + warp + u_time * speed + phase) * amp;
}

float WaterSurfaceHeight(in vec2 xz) {
    float swell = Terrain(xz).x * 0.22;
    float longWave = sin(dot(xz, normalize(vec2(0.9, 0.35))) * 0.028 + u_time * 0.38) * 0.95;
    float crossWave = sin(dot(xz, normalize(vec2(-0.45, 1.0))) * 0.022 - u_time * 0.31) * 0.58;
    float rippleA = RippleBand(xz, vec2(1.0, 0.18), 0.19, 1.25, 0.20, 0.0);
    float rippleB = RippleBand(xz, vec2(0.72, -0.56), 0.26, -1.05, 0.15, 3.1);
    float rippleC = RippleBand(xz, vec2(-0.35, 1.0), 0.34, 1.58, 0.08, 6.2);
    float rippleNoise = (FractalNoise(xz * 0.11 + vec2(-u_time * 0.06, u_time * 0.045)) - 0.5) * 0.18;
    return swell + longWave + crossWave + rippleA + rippleB + rippleC + rippleNoise;
}

float CameraWaterHeight(in vec2 xz) {
    float swell = Terrain(xz).x * 0.18;
    float longWave = sin(dot(xz, normalize(vec2(0.9, 0.35))) * 0.024 + u_time * 0.24) * 0.42;
    float crossWave = sin(dot(xz, normalize(vec2(-0.45, 1.0))) * 0.018 - u_time * 0.19) * 0.24;
    float drift = (FractalNoise(xz * 0.028 + vec2(-u_time * 0.02, u_time * 0.016)) - 0.5) * 0.12;
    return swell + longWave + crossWave + drift;
}

vec3 WaterNormal(in vec2 xz) {
    vec2 stepSize = vec2(0.35, 0.0);
    float center = WaterSurfaceHeight(xz);
    float sampleX = WaterSurfaceHeight(xz + stepSize.xy);
    float sampleZ = WaterSurfaceHeight(xz + stepSize.yx);
    vec3 tangentX = vec3(stepSize.x, sampleX - center, 0.0);
    vec3 tangentZ = vec3(0.0, sampleZ - center, stepSize.x);
    return normalize(cross(tangentZ, tangentX));
}

vec2 Map(in vec3 p) {
    vec2 h = vec2(WaterSurfaceHeight(p.xz), 0.0);
    return vec2(p.y - h.x, h.y);
}

float StableStarLayer(in vec2 uv, in float scale, in float threshold, in float shimmerSpeed) {
    vec2 grid = uv * scale;
    vec2 cell = floor(grid);
    vec2 local = fract(grid) - 0.5;
    float seed = Hash(cell);
    float star = smoothstep(threshold, 1.0, seed);
    float flare = 1.0 - smoothstep(0.018, 0.24, length(local));
    float shimmer = 0.55 + 0.45 * sin(u_time * shimmerSpeed + seed * 31.0);
    float core = 1.0 - smoothstep(0.0, 0.07, length(local));
    return star * (flare * flare * 0.85 + core * 1.3) * shimmer;
}

float ShootingStarLayer(in vec2 uv, in float timeScale, in float timeOffset) {
    float timeline = u_time * timeScale + timeOffset;
    float phase = floor(timeline);
    float life = fract(timeline);
    float event = step(0.9, Hash(phase * 17.31 + timeOffset * 9.7));
    float visibility = smoothstep(0.02, 0.09, life) * (1.0 - smoothstep(0.12, 0.22, life));

    float side = mix(-1.6, 1.6, Hash(phase + timeOffset * 1.3));
    float height = mix(0.55, 1.4, Hash(phase * 1.7 + timeOffset * 2.1));
    float angle = mix(-0.65, 0.65, Hash(phase * 2.3 + timeOffset * 4.6));
    vec2 dir = normalize(vec2(cos(angle), -0.55 + sin(angle) * 0.35));
    vec2 head = vec2(side, height) + dir * (life * 2.2 - 0.35);
    vec2 delta = uv - head;
    vec2 tangent = vec2(-dir.y, dir.x);
    float along = -dot(delta, dir);
    float across = dot(delta, tangent);

    float core = exp(-length(vec2(along * 18.0, across * 85.0)));
    float tail = exp(-max(along, 0.0) * 5.4 - abs(across) * 80.0) * step(0.0, along) * (1.0 - smoothstep(0.25, 0.8, along));
    return event * visibility * (core * 0.55 + tail * 0.65);
}

vec3 GetSky(in vec3 rd, in vec2 xy) {
    float sunAmount = max(dot(rd, sunLight), 0.0);
    float v = pow(1.0 - max(rd.y, 0.0), 6.0);
    vec3 tint = mix(u_skyTopColor, u_skyHorizonColor, v);
    vec3 sky = tint;

    float lightningPhase = floor(u_time * 0.10);
    float lightningEvent = step(0.985, Hash(lightningPhase + 17.0));
    float lightningFlash = lightningEvent * smoothstep(0.0, 0.08, fract(u_time * 0.10));
    float skyFlash = lightningFlash * clamp(1.0 - max(rd.y, 0.0), 0.0, 1.0);
    sky += vec3(0.96, 0.98, 1.0) * skyFlash * 0.9;

    vec2 starUv = rd.xz / max(rd.y + 0.32, 0.22);
    float night = smoothstep(0.05, 0.42, 1.0 - max(rd.y, 0.0));
    float starField =
        StableStarLayer(starUv + vec2(0.02, -0.01), 20.0, 0.9928, 1.7) +
        StableStarLayer(starUv * 1.5 - vec2(0.04, 0.03), 32.0, 0.9951, 2.3) * 0.95 +
        StableStarLayer(starUv * 2.1 + vec2(0.09, 0.05), 52.0, 0.9970, 3.1) * 0.7;
    vec3 starTint = mix(vec3(0.70, 0.78, 1.0), vec3(1.0), Hash(floor(starUv * 18.0)));
    float shootingStars =
        ShootingStarLayer(starUv, 0.11, 0.0) +
        ShootingStarLayer(starUv * 1.08 + vec2(0.3, -0.2), 0.09, 3.7) * 0.55 +
        ShootingStarLayer(starUv * 0.92 - vec2(0.25, 0.15), 0.13, 7.1) * 0.45;
    sky += starTint * starField * night * 1.25;
    sky += vec3(0.92, 0.96, 1.0) * shootingStars * night * 0.55;

    vec3 moonDir = sunLight;
    float moonAmount = max(dot(rd, moonDir), 0.0);
    float moonDisc = smoothstep(0.9964, 0.9992, moonAmount);
    float moonGlow = pow(moonAmount, 90.0) * 0.24;
    vec3 moonColor = mix(vec3(0.78, 0.82, 0.9), vec3(0.96, 0.98, 1.0), 0.6);
    sky += moonColor * moonDisc * 1.15;
    sky += moonColor * moonGlow;

    return clamp(sky, 0.0, 1.0);
}

vec3 ApplyFog(in vec3 rgb, in float dis, in vec3 dir, in vec2 xy) {
    float fogAmount = clamp(dis * dis * 0.0000012, 0.0, 1.0);
    vec3 fogTarget = mix(GetSky(dir, xy), u_fogColor, 0.35);
    return mix(rgb, fogTarget, fogAmount);
}

float CircleOfConfusion(float t) {
    return max(t * 0.04, (2.0 / u_resolution.y) * (1.0 + t));
}

float Linstep(float a, float b, float t) {
    return clamp((t - a) / (b - a), 0.0, 1.0);
}

void DoLighting(inout vec3 mat, in vec3 normal) {
    float h = dot(sunLight, normal);
    mat *= u_sunColor * (max(h, 0.0) + 0.2);
}

vec3 TerrainColour(vec3 pos, vec3 dir, vec3 normal, float dis, float type) {
    vec3 mat = u_grassPrimaryColor;
    if (type == 0.0) {
        vec3 skyReflection = GetSky(reflect(dir, normal), vec2(0.5, 0.5));
        float fresnel = pow(clamp(1.0 - max(dot(normal, -dir), 0.0), 0.0, 1.0), 3.0);
        float foamMask = smoothstep(0.45, 0.88, FractalNoise(pos.xz * 0.05 + vec2(u_time * 0.04, u_time * 0.03)));
        vec3 deepWater = mix(u_grassPrimaryColor * vec3(0.35, 0.42, 0.52), u_grassPrimaryColor * vec3(0.75, 0.88, 1.05), Noise(pos.xz * 0.012));
        vec3 shallowTint = mix(u_grassSecondaryColor * vec3(0.72, 0.86, 1.0), u_grassSecondaryColor * vec3(1.0, 1.08, 1.12), max(normal.y, 0.0));
        vec3 foam = vec3(0.72, 0.82, 0.9) * foamMask * (1.0 - smoothstep(0.7, 0.98, normal.y));
        mat = mix(deepWater, shallowTint, 0.45 + normal.y * 0.25);
        mat = mix(mat, skyReflection, 0.48 + fresnel * 0.5);
        mat += foam * 0.35;
        DoLighting(mat, normal);
    }
    return ApplyFog(mat, dis, dir, vec2(0.5, 0.5));
}

float BinarySubdivision(in vec3 rO, in vec3 rD, float t, float oldT) {
    float halfwayT = 0.0;
    for (int n = 0; n < 5; n++) {
        halfwayT = (oldT + t) * 0.5;
        if (Map(rO + halfwayT * rD).x < 0.05) t = halfwayT;
        else oldT = halfwayT;
    }
    return t;
}

bool Scene(in vec3 rO, in vec3 rD, out float resT, out float type) {
    float t = 5.0, oldT = 0.0;
    vec2 h;
    bool hit = false;
    for (int j = 0; j < 80; j++) {
        vec3 p = rO + t * rD;
        if (p.y < 105.0 && !hit) {
            h = Map(p);
            if (h.x < 0.05) {
                resT = BinarySubdivision(rO, rD, t, oldT);
                type = h.y;
                hit = true;
            } else {
                float delta = max(0.04, 0.35 * h.x) + (t * 0.04);
                oldT = t;
                t += delta;
            }
        }
    }
    return hit;
}

vec3 CameraPath(float t) {
    vec2 p = vec2(200.0 * sin(3.54 * t), 200.0 * cos(2.0 * t));
    return vec3(p.x + 55.0, 3.4 + sin(t * 0.28) * 0.9, -94.0 + p.y);
}

vec3 PostEffects(vec3 rgb, vec2 xy) {
    rgb = pow(rgb, vec3(0.45));
    float contrast = u_postFx.x;
    float saturation = u_postFx.y;
    float brightness = u_postFx.z;
    float vignette = u_postFx.w;
    rgb = mix(
        vec3(0.5),
        mix(vec3(dot(vec3(0.2125, 0.7154, 0.0721), rgb * brightness)), rgb * brightness, saturation),
        contrast
    );
    float edge = pow(40.0 * xy.x * xy.y * (1.0 - xy.x) * (1.0 - xy.y), 0.2);
    rgb *= mix(1.0 - vignette, 0.4 + 0.5 * edge, clamp(vignette, 0.0, 1.5));
    return rgb;
}

void main() {
    float gTime = (u_time * 5.0 * max(u_speed, 0.01) + 2352.0) * 0.006;
    vec2 xy = gl_FragCoord.xy / u_resolution.xy;
    vec2 uv = (-1.0 + 2.0 * xy) * vec2(u_resolution.x / u_resolution.y, 1.0);

    vec3 cameraPos = CameraPath(gTime);
    vec3 camTar = CameraPath(gTime + 0.03);
    vec3 camAhead = CameraPath(gTime + 0.075);
    vec3 camFar = CameraPath(gTime + 0.13);
    vec3 camFarther = CameraPath(gTime + 0.19);
    float surfaceHeight = CameraWaterHeight(cameraPos.xz);
    float targetSurfaceHeight = CameraWaterHeight(camTar.xz);
    float aheadSurfaceHeight = CameraWaterHeight(camAhead.xz);
    float farSurfaceHeight = CameraWaterHeight(camFar.xz);
    float fartherSurfaceHeight = CameraWaterHeight(camFarther.xz);
    float smoothedSurface = surfaceHeight * 0.28 + targetSurfaceHeight * 0.24 + aheadSurfaceHeight * 0.22 + farSurfaceHeight * 0.16 + fartherSurfaceHeight * 0.10;
    float forwardSurface = targetSurfaceHeight * 0.34 + aheadSurfaceHeight * 0.28 + farSurfaceHeight * 0.22 + fartherSurfaceHeight * 0.16;
    cameraPos.y = smoothedSurface + 5.35;
    camTar.y = forwardSurface + 5.0;

    float roll = 0.4 * sin(gTime + 0.5);
    vec3 cw = normalize(camTar - cameraPos);
    vec3 cp = vec3(sin(roll), cos(roll), 0.0);
    vec3 cu = cross(cw, cp);
    vec3 cv = cross(cu, cw);
    vec3 dir = normalize(uv.x * cu + uv.y * cv + 1.3 * cw);

    vec3 col;
    float distance;
    float type;
    if (!Scene(cameraPos, dir, distance, type)) {
        col = GetSky(dir, xy);
    } else {
        vec3 pos = cameraPos + distance * dir;
        vec3 nor = WaterNormal(pos.xz);
        col = TerrainColour(pos, dir, nor, distance, type);
    }

    col = PostEffects(col, xy);
    outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
