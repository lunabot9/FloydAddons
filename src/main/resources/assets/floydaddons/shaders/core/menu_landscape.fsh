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

const float TAU = 6.28318530718;
const vec3 SITE_INK = vec3(0.0392, 0.0275, 0.0118);       // #0A0703
const vec3 SITE_PANEL = vec3(0.1059, 0.0784, 0.0314);     // #1B1408
const vec3 SITE_AMBER = vec3(1.0000, 0.7216, 0.3020);     // #FFB84D
const vec3 SITE_ORANGE = vec3(0.9686, 0.5098, 0.1176);    // #F7821E
const vec3 SITE_IVORY = vec3(1.0000, 0.9529, 0.8588);     // #FFF3DB

float Hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

float Hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

vec2 Hash2(vec2 p) {
    return fract(sin(vec2(
        dot(p, vec2(127.1, 311.7)),
        dot(p, vec2(269.5, 183.3))
    )) * 43758.5453123);
}

float Noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(Hash(i), Hash(i + vec2(1.0, 0.0)), f.x),
        mix(Hash(i + vec2(0.0, 1.0)), Hash(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

float Fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.52;
    mat2 rotation = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 5; i++) {
        value += Noise(p) * amplitude;
        p = rotation * p * 2.03 + vec2(17.1, 9.2);
        amplitude *= 0.50;
    }
    return value;
}

float PanoramaAngle(vec3 direction) {
    return atan(direction.x, direction.z);
}

float DefinedStarLayer(vec3 direction, float scale, float threshold, float time) {
    float angle01 = PanoramaAngle(direction) / TAU + 0.5;
    vec2 grid = vec2(angle01, direction.y * 0.70 + 0.20) * vec2(scale * 2.0, scale);
    vec2 cell = floor(grid);
    vec2 starPosition = mix(vec2(0.18), vec2(0.82), Hash2(cell + 4.7));
    vec2 local = fract(grid) - starPosition;
    float seed = Hash(cell + 19.7);
    float selected = smoothstep(threshold, 1.0, seed);
    float distanceToStar = length(local);
    float core = 1.0 - smoothstep(0.010, 0.044, distanceToStar);
    float halo = exp(-distanceToStar * 24.0);
    float horizontalSpike = exp(-abs(local.y) * 85.0) * exp(-abs(local.x) * 13.0);
    float verticalSpike = exp(-abs(local.x) * 85.0) * exp(-abs(local.y) * 13.0);
    float twinkle = 0.82 + 0.18 * sin(time * (0.72 + seed * 1.1) + seed * 53.0);
    return selected * (core * 1.65 + halo * 0.34 + (horizontalSpike + verticalSpike) * 0.20) * twinkle;
}

vec3 DefinedStarField(vec3 direction, float time) {
    float elevationMask = smoothstep(0.055, 0.19, direction.y);
    float stars = DefinedStarLayer(direction, 78.0, 0.982, time)
        + DefinedStarLayer(direction + vec3(0.0, 0.014, 0.0), 128.0, 0.989, time * 1.17) * 0.78
        + DefinedStarLayer(direction + vec3(0.0, 0.027, 0.0), 184.0, 0.994, time * 1.31) * 0.54;
    float tintSeed = Hash(floor(PanoramaAngle(direction) * 41.0));
    vec3 starTint = mix(SITE_IVORY, SITE_AMBER, tintSeed * 0.38);
    return starTint * stars * elevationMask;
}

vec4 AuroraCurtain(vec3 direction, float time) {
    float elevation = direction.y;
    float angle = PanoramaAngle(direction);
    vec2 panoramaCircle = normalize(direction.xz);
    float flowTime = time * 0.075;

    float broadFlow = Fbm(vec2(
        panoramaCircle.x * 2.4 - flowTime * 0.24 + elevation * 0.31,
        panoramaCircle.y * 2.4 + flowTime * 0.055 + elevation * 1.45
    ));
    float fineFlow = Fbm(vec2(
        panoramaCircle.x * 6.2 + flowTime * 0.39 + elevation * 0.47 + 4.7,
        panoramaCircle.y * 6.2 - flowTime * 0.085 + elevation * 3.10
    ));
    float streamFlow = Fbm(vec2(
        panoramaCircle.x * 12.4 - flowTime * 0.72 + fineFlow * 0.35,
        panoramaCircle.y * 12.4 + elevation * 1.20 + flowTime * 0.045 + 9.1
    ));
    float foldedAngle = angle
        + flowTime * 0.085
        + sin(angle * 3.0 - flowTime * 1.15 + broadFlow * 1.7) * 0.21
        + sin(angle * 7.0 + flowTime * 0.73 + fineFlow * 2.1) * 0.075;

    float centerA = 0.38
        + sin(foldedAngle * 2.0 - flowTime * 0.68) * 0.090
        + (broadFlow - 0.5) * 0.11;
    float centerB = 0.55
        + sin(foldedAngle * 3.0 + flowTime * 0.51 + 1.8) * 0.075
        + (fineFlow - 0.5) * 0.060;
    float centerC = 0.25
        + sin(foldedAngle * 4.0 - flowTime * 0.37 + 4.2) * 0.052
        + (streamFlow - 0.5) * 0.035;

    float bandA = exp(-pow(abs(elevation - centerA) * 13.0, 1.30));
    float bandB = exp(-pow(abs(elevation - centerB) * 17.0, 1.38));
    float bandC = exp(-pow(abs(elevation - centerC) * 20.0, 1.28));
    float folds = 0.5 + 0.5 * sin(
        angle * 23.0
        + elevation * 10.0
        + sin(angle * 8.0 + elevation * 5.0 - flowTime * 3.2) * 2.4
        + fineFlow * 4.2
        + streamFlow * 3.4
        - flowTime * 5.0
    );
    float narrowFolds = 0.5 + 0.5 * sin(
        angle * 47.0 + flowTime * 8.0 + broadFlow * 5.0 + streamFlow * 4.0
    );
    float curtainStrands = mix(0.38, 1.0, pow(folds, 1.8))
        * mix(0.68, 1.0, pow(narrowFolds, 3.0));
    float verticalShimmer = 0.80 + 0.20 * sin(
        elevation * 54.0 - flowTime * 4.8 + fineFlow * 3.2 + streamFlow * 2.0
    );
    float breathing = 0.86 + 0.14 * sin(time * 0.16 + broadFlow * 3.4);
    float skyMask = smoothstep(0.07, 0.18, elevation)
        * (1.0 - smoothstep(0.88, 1.00, elevation));
    float intensity = (bandA * 0.88 + bandB * 0.55 + bandC * 0.42)
        * curtainStrands * verticalShimmer * breathing * skyMask;

    vec3 color = SITE_AMBER * bandA * 0.78
        + SITE_ORANGE * bandB * 0.58
        + SITE_IVORY * bandC * 0.32;
    color = mix(color, SITE_IVORY * (bandA + bandB), pow(narrowFolds, 6.0) * 0.15);
    return vec4(
        color * curtainStrands * verticalShimmer * breathing * skyMask,
        clamp(intensity, 0.0, 1.0)
    );
}

vec3 SkyColor(vec3 direction, float time) {
    float elevation = max(direction.y, 0.0);
    float skyBlend = smoothstep(0.0, 0.82, pow(elevation, 0.72));
    vec3 horizon = mix(SITE_PANEL, u_skyHorizonColor, 0.16) * 0.33;
    vec3 zenith = mix(SITE_INK, u_skyTopColor, 0.10) * 0.27;
    vec3 sky = mix(horizon, zenith, skyBlend);

    sky += DefinedStarField(direction, time) * 0.96;
    vec4 aurora = AuroraCurtain(direction, time);
    sky += aurora.rgb * 0.74;
    sky += aurora.rgb * aurora.a * 0.24;

    float horizonGlow = exp(-elevation * 28.0);
    sky += mix(SITE_ORANGE, u_sunColor, 0.16) * horizonGlow * 0.025;
    return sky;
}

float OceanWaveHeight(vec2 position, float time) {
    vec2 broadDrift = vec2(time * 0.100, -time * 0.074);
    vec2 warp = vec2(
        Noise(position * 0.017 + broadDrift + vec2(4.1, 9.7)),
        Noise(position * 0.019 - broadDrift + vec2(18.2, 2.6))
    ) - 0.5;
    vec2 warpedPosition = position + warp * 11.0;
    float waveGrouping = 0.65 + 0.35 * Noise(
        position * 0.011 + vec2(-time * 0.011, time * 0.008) + 31.7
    );

    float waves = 0.0;
    waves += sin(dot(warpedPosition, normalize(vec2(1.00, 0.24))) * 0.060 - time * 0.28 + warp.y * 1.8) * 0.82;
    waves += sin(dot(warpedPosition, normalize(vec2(0.48, 1.00))) * 0.083 - time * 0.36 + 1.7) * 0.55;
    waves += sin(dot(warpedPosition, normalize(vec2(-0.73, 0.68))) * 0.115 - time * 0.48 + warp.x * 2.3) * 0.34;
    waves += sin(dot(warpedPosition, normalize(vec2(0.91, -0.42))) * 0.157 - time * 0.61 + 4.1) * 0.20;

    float brokenChop = sin(
        dot(warpedPosition, normalize(vec2(0.28, 1.00))) * 0.241
        - time * 0.84
        + waves * 0.73
        + warp.y * 3.1
    ) * 0.120;
    return waves * waveGrouping + brokenChop;
}

float RandomizedRippleHeight(vec2 position, float time) {
    vec2 noiseFlow = vec2(time * 0.19, -time * 0.13);
    float phaseNoise = Noise(position * 0.061 + noiseFlow + 7.3);
    vec2 warpedPosition = position + vec2(
        Noise(position * 0.083 - noiseFlow + 13.1),
        Noise(position * 0.077 + noiseFlow.yx + 21.8)
    ) * 3.8;
    float breakup = 0.42 + 0.58 * Noise(position * 0.145 + noiseFlow * 0.37 + 5.4);
    float ripples = sin(
        dot(warpedPosition, normalize(vec2(1.00, 0.37))) * 0.72
        - time * 1.03
        + phaseNoise * TAU
    ) * 0.040;
    ripples += sin(
        dot(warpedPosition, normalize(vec2(-0.23, 1.00))) * 0.96
        - time * 1.31
        + phaseNoise * 4.7
    ) * 0.027;
    ripples += (Noise(warpedPosition * 0.31 + noiseFlow * 0.55) - 0.5) * 0.058;
    return ripples * breakup;
}

float OceanSurfaceHeight(vec2 position, float time) {
    return OceanWaveHeight(position, time) + RandomizedRippleHeight(position, time);
}

float TraceOceanSurface(vec3 cameraPosition, vec3 direction, float time) {
    float downward = max(-direction.y, 0.003);
    float distanceToWater = clamp(cameraPosition.y / downward, 0.10, 2600.0);
    for (int iteration = 0; iteration < 6; iteration++) {
        vec3 position = cameraPosition + direction * distanceToWater;
        float heightGap = position.y - OceanSurfaceHeight(position.xz, time);
        distanceToWater = clamp(distanceToWater + heightGap / downward * 0.62, 0.10, 2600.0);
    }
    return distanceToWater;
}

vec3 WaterNormal(vec2 position, float distanceToWater, float time) {
    float sampleDistance = mix(0.18, 3.40, clamp(distanceToWater * 0.0010, 0.0, 1.0));
    float centerHeight = OceanSurfaceHeight(position, time);
    float xHeight = OceanSurfaceHeight(position + vec2(sampleDistance, 0.0), time);
    float zHeight = OceanSurfaceHeight(position + vec2(0.0, sampleDistance), time);
    return normalize(vec3(centerHeight - xHeight, sampleDistance, centerHeight - zHeight));
}

vec3 OceanColor(vec3 cameraPosition, vec3 direction, float time) {
    float distanceToWater = TraceOceanSurface(cameraPosition, direction, time);
    vec3 position = cameraPosition + direction * distanceToWater;
    float surfaceHeight = OceanSurfaceHeight(position.xz, time);
    vec3 normal = WaterNormal(position.xz, distanceToWater, time);
    float farNormalFade = smoothstep(320.0, 1500.0, distanceToWater);
    normal = normalize(mix(normal, vec3(0.0, 1.0, 0.0), farNormalFade * 0.72));
    vec3 reflectionDirection = normalize(reflect(direction, normal));
    reflectionDirection.y = max(reflectionDirection.y, 0.002);
    reflectionDirection = normalize(reflectionDirection);

    vec3 reflection = SkyColor(reflectionDirection, time) * 1.10;
    float facing = clamp(dot(normal, -direction), 0.0, 1.0);
    float fresnel = pow(1.0 - facing, 2.65);
    float depthPattern = Fbm(position.xz * 0.018 + vec2(time * 0.004, -time * 0.003));
    vec3 deepWater = mix(SITE_INK, u_grassPrimaryColor, 0.18) * 0.57;
    vec3 shallowWater = mix(SITE_PANEL, u_grassSecondaryColor, 0.16) * 0.64;
    vec3 water = mix(deepWater, shallowWater, depthPattern * 0.42);
    water = mix(water, reflection, clamp(0.82 + fresnel * 0.16, 0.0, 0.988));

    vec4 reflectedAurora = AuroraCurtain(reflectionDirection, time);
    water += reflectedAurora.rgb * reflectedAurora.a * 0.055;

    float crestNoise = smoothstep(
        0.36,
        0.78,
        Noise(position.xz * 0.132 + vec2(time * 0.031, -time * 0.024) + 12.6)
    );
    float crestHeight = smoothstep(0.52, 1.38, surfaceHeight);
    float crestSlope = smoothstep(0.025, 0.28, 1.0 - normal.y);
    float rippleCrest = crestHeight * crestSlope * mix(0.24, 1.0, crestNoise);
    water += mix(SITE_IVORY, SITE_AMBER, 0.58 + fresnel * 0.20)
        * rippleCrest * (0.055 + fresnel * 0.075);

    float distanceFog = 1.0 - exp(-distanceToWater * 0.00115);
    vec3 farOcean = mix(SITE_INK, mix(SITE_PANEL, u_fogColor, 0.08), 0.42) * 0.70;
    vec3 foggedWater = mix(water, farOcean, distanceFog * 0.62);
    float horizonBlend = 1.0 - smoothstep(0.006, 0.050, -direction.y);
    vec3 horizonDirection = normalize(vec3(direction.x, max(-direction.y * 0.12, 0.003), direction.z));
    vec3 horizonSky = SkyColor(horizonDirection, time);
    return mix(foggedWater, horizonSky, horizonBlend);
}

vec2 ForwardCameraTrack(float time) {
    float travel = time * 4.20;
    return vec2(0.0, travel);
}

vec3 CameraPath(float time) {
    vec2 track = ForwardCameraTrack(time);
    vec2 nearTrack = ForwardCameraTrack(time + 0.80);
    vec2 forward = normalize(nearTrack - track);
    float localWave = OceanSurfaceHeight(track, time);
    float approachingWave = OceanSurfaceHeight(track + forward * 2.8, time);
    float followedWave = mix(localWave, approachingWave, 0.38);
    return vec3(track.x, followedWave + 5.10, track.y);
}

vec3 CameraTarget(float time) {
    vec2 track = ForwardCameraTrack(time);
    vec2 pathTangent = vec2(0.0, 1.0);
    vec2 waveProbe = track + pathTangent * 14.0;
    vec2 targetTrack = track + pathTangent * 58.0;
    float targetWave = OceanSurfaceHeight(waveProbe, time);
    float targetHeight = targetWave + 27.5;
    return vec3(targetTrack.x, targetHeight, targetTrack.y);
}

vec3 PostEffects(vec3 color, vec2 screenUv) {
    color = pow(max(color, vec3(0.0)), vec3(1.0 / 1.95));
    float contrast = u_postFx.x;
    float saturation = u_postFx.y;
    float brightness = u_postFx.z;
    float vignette = u_postFx.w;
    float luminance = dot(color, vec3(0.2125, 0.7154, 0.0721));
    color = mix(vec3(luminance), color, saturation);
    color = (color - 0.5) * contrast + 0.5;
    color *= brightness * 0.70;

    vec2 centered = screenUv * 2.0 - 1.0;
    float edge = smoothstep(0.28, 1.35, dot(centered, centered));
    color *= 1.0 - edge * clamp(vignette, 0.0, 1.5) * 0.25;
    return color;
}

void main() {
    float sceneTime = u_time * max(u_speed, 0.01);
    vec2 screenUv = gl_FragCoord.xy / u_resolution.xy;
    vec2 uv = (screenUv * 2.0 - 1.0) * vec2(u_resolution.x / u_resolution.y, 1.0);

    vec3 cameraPosition = CameraPath(sceneTime);
    vec3 cameraTarget = CameraTarget(sceneTime);
    vec3 forward = normalize(cameraTarget - cameraPosition);
    vec3 right = normalize(cross(forward, vec3(0.0, 1.0, 0.0)));
    vec3 up = normalize(cross(right, forward));
    vec3 direction = normalize(forward * 1.48 + right * uv.x + up * uv.y);

    vec3 color = direction.y < 0.0
        ? OceanColor(cameraPosition, direction, sceneTime)
        : SkyColor(direction, sceneTime);
    color = PostEffects(color, screenUv);
    outColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
