#version 300 es
precision highp float;

#include "common.glsl"

// The scene is a cut-away aquarium: the play plane z = 0 is the glass. Above the waterline we
// see the sky; below it, the water volume behind the glass, with the underside of the surface
// receding into the distance, light rays and caustics.
uniform mat4 uInvViewProj;
uniform vec3 uWaterTop;     // water colour at the depth seen at the top of the screen
uniform vec3 uWaterBottom;  // ...and at the bottom (from the colour script)
uniform vec2 uDepthRange;   // those two depths
uniform float uAtmosphere;  // 1 in the Nightmare atmosphere
in vec2 vNdc;
out vec4 fragColor;

vec3 skyColor(vec3 p, vec3 dir) {
    float t = clamp(p.y / 14.0, 0.0, 1.0);
    vec3 col = mix(vec3(0.84, 0.95, 1.0), vec3(0.45, 0.76, 0.94), t);
    // Soft drifting clouds.
    float c = noise2(vec2(p.x * 0.12 + uTime * 0.03, p.y * 0.35)) * 0.6 + noise2(vec2(p.x * 0.3 - uTime * 0.02, p.y * 0.8)) * 0.4;
    col = mix(col, vec3(1.0), smoothstep(0.55, 0.8, c) * smoothstep(1.5, 5.0, p.y) * 0.85);
    // Sun.
    float sun = length(vec2(p.x - 3.0, p.y - 11.0));
    col += vec3(1.0, 0.95, 0.75) * (smoothstep(2.4, 1.6, sun) * 0.6 + smoothstep(9.0, 0.0, sun) * 0.15);
    return col;
}

void main() {
    vec4 nearP = uInvViewProj * vec4(vNdc, -1.0, 1.0);
    vec4 farP = uInvViewProj * vec4(vNdc, 1.0, 1.0);
    nearP /= nearP.w;
    farP /= farP.w;
    vec3 o = uCamPos;
    vec3 dir = normalize(farP.xyz - nearP.xyz);
    float t0 = -o.z / dir.z;
    vec3 p0 = o + dir * t0;
    float wave = 0.10 * sin(p0.x * 1.3 + uTime * 2.0) + 0.06 * sin(p0.x * 3.1 - uTime * 1.4);
    if (p0.y > wave) {
        fragColor = vec4(skyColor(p0, dir), 1.0);
        return;
    }
    float depth = -p0.y;
    float k = clamp((depth - uDepthRange.x) / max(uDepthRange.y - uDepthRange.x, 1.0), 0.0, 1.0);
    vec3 col = mix(uWaterTop, uWaterBottom, k);
    // Looking up into the water behind the glass: the silvery underside of the surface.
    if (dir.y > 0.0) {
        float t1 = -o.y / dir.y;
        float through = t1 - t0;
        if (through > 0.0) {
            vec3 hit = o + dir * t1;
            vec2 q = hit.xz * 0.35;
            // Caustic network: smooth wobbling lines.
            float c1 = sin(q.x * 2.0 + sin(q.y * 1.6 + uTime) * 1.4) * sin(q.y * 2.0 + sin(q.x * 1.3 - uTime * 0.8) * 1.4);
            float lines = smoothstep(0.75, 1.0, abs(c1));
            vec3 surf = mix(col * 1.25 + vec3(0.05, 0.08, 0.1), vec3(0.85, 0.97, 1.0), clamp(lines, 0.0, 1.0) * 0.6);
            col = mix(surf, col, 1.0 - exp(-through / 10.0));
        }
    }
    // Foam along the waterline.
    col = mix(col, vec3(1.0), smoothstep(0.12, 0.0, wave - p0.y) * 0.85);
    // Sun rays slanting down from the surface, fading with depth.
    float rays = 0.0;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float x = p0.x + depth * (0.18 + 0.05 * fk) + sin(uTime * 0.3 + fk * 1.7) * 0.8;
        rays += pow(max(0.0, sin(x * (0.35 + 0.12 * fk) + fk * 2.0)), 18.0);
    }
    col += vec3(1.0, 0.96, 0.82) * rays * 0.14 * smoothstep(90.0, 0.0, depth);
    // Caustic shimmer near the surface.
    float c = sin(p0.x * 1.7 + sin(depth * 1.3 + uTime * 1.1) * 1.5) * sin(depth * 1.9 + sin(p0.x * 1.1 - uTime) * 1.5);
    col += vec3(0.7, 0.9, 1.0) * smoothstep(0.8, 1.0, abs(c)) * 0.07 * smoothstep(35.0, 0.0, depth);
    // Gentle vignette for depth, heavier the deeper you are.
    vec2 uv = gl_FragCoord.xy / uResolution;
    float deep = smoothstep(150.0, 550.0, depth);
    col *= 1.0 - (0.18 + 0.3 * deep) * pow(length(uv - 0.5) * 1.3, 2.0);
    // Nightmare: a slow, sick red pulse at the edges of the deep water.
    if (uAtmosphere > 0.5) {
        float pulse = 0.5 + 0.5 * sin(uTime * 1.3);
        col += vec3(0.35, 0.0, 0.02) * deep * pow(length(uv - 0.5) * 1.4, 3.0) * (0.5 + 0.5 * pulse);
    }
    fragColor = vec4(col, 1.0);
}
