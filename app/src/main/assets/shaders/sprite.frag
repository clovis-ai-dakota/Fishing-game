#version 300 es
precision highp float;

#include "common.glsl"

// Camera-facing particles and ribbons, built on the CPU. vShape picks the look:
// 0 soft dot, 1 bubble ring, 2 four-point twinkle, 3 coin, 4 halo, 10+ fishing line skins.
uniform float uAdditive;
in vec2 vUv;
in float vShape;
in vec4 vColor;
in float vFog;
out vec4 fragColor;

void main() {
    vec2 q = vUv * 2.0 - 1.0;
    float r = length(q);
    float a = 0.0;
    vec3 col = vColor.rgb;
    if (vShape < 0.5) {
        a = smoothstep(1.0, 0.4, r);
    } else if (vShape < 1.5) {
        a = smoothstep(1.0, 0.85, r) * smoothstep(0.55, 0.75, r) + 0.25 * smoothstep(0.9, 0.0, r) + 0.6 * smoothstep(0.25, 0.1, length(q - vec2(-0.35, 0.35)));
    } else if (vShape < 2.5) {
        float star = max(1.0 - abs(q.x) * 6.0 - abs(q.y), 1.0 - abs(q.y) * 6.0 - abs(q.x));
        a = clamp(star, 0.0, 1.0) + smoothstep(0.5, 0.0, r) * 0.6;
    } else if (vShape < 3.5) {
        a = smoothstep(1.0, 0.9, r);
        col = mix(col * 0.75, col, smoothstep(0.75, 0.6, r));
    } else if (vShape < 4.5) {
        a = pow(max(0.0, 1.0 - r), 2.0);
    } else {
        // Fishing line: vUv.x = meters along the line, vUv.y = across (0..1).
        float across = abs(vUv.y - 0.5) * 2.0;
        a = smoothstep(1.0, 0.6, across);
        float mode = vShape - 10.0;
        if (mode > 0.5 && mode < 1.5) {
            // Giant's braid: twisted strands and a shimmer running down.
            float twist = step(0.5, fract(vUv.x * 2.2 - vUv.y * 0.6 - uTime * 0.4));
            col = mix(vec3(0.88, 0.97, 0.98), vec3(0.30, 0.82, 0.88), twist);
            col += vec3(1.0) * smoothstep(0.85, 1.0, sin(vUv.x * 0.6 - uTime * 5.0)) * 0.8;
        } else if (mode > 2.5) {
            // Golden line with glints.
            col = mix(vec3(1.0, 0.63, 0.0), vec3(1.0, 0.85, 0.3), smoothstep(0.8, 0.0, across));
            col += vec3(1.0) * smoothstep(0.95, 1.0, sin(vUv.x * 0.9 - uTime * 4.0));
        }
        // mode 0 (classic) and 2 (rainbow) use the vertex colour as is.
    }
    a *= vColor.a * (1.0 - vFog);
    if (a < 0.003) discard;
    fragColor = uAdditive > 0.5 ? vec4(col * a, 1.0) : vec4(col, a);
}
