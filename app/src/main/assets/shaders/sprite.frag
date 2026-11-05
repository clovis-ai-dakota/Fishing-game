#version 300 es
precision highp float;

#include "common.glsl"

// Camera-facing particles and ribbons, built on the CPU. vShape picks the look:
// 0 soft dot, 1 bubble ring, 2 four-point twinkle, 3 coin, 4 halo, 5 heart, 6 petal, 7 flat,
// 10+ fishing line skins.
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
    } else if (vShape < 5.5) {
        // A heart: the classic implicit curve (x² + y² - 1)³ = x²y³, lobes up.
        vec2 h = q * 1.3 + vec2(0.0, 0.2);
        float k = dot(h, h) - 1.0;
        float f = k * k * k - h.x * h.x * h.y * h.y * h.y;
        a = smoothstep(0.03, -0.03, f);
        col = mix(col, vec3(1.0), 0.5 * smoothstep(0.18, 0.0, length(q - vec2(-0.3, 0.3))));
    } else if (vShape > 6.5 && vShape < 7.5) {
        // Flat: the vertex colour as is.
        a = 1.0;
    } else if (vShape < 6.5) {
        // A petal: a pointed ellipse, slightly pinker at its base.
        vec2 e = vec2(q.x * 0.9, q.y * 1.8);
        a = smoothstep(1.0, 0.85, length(e) + abs(q.x) * 0.25);
        col = mix(col, col * vec3(1.0, 0.8, 0.9), smoothstep(-0.2, -0.8, q.x));
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
        } else if (mode > 2.5 && mode < 3.5) {
            // Golden line with glints.
            col = mix(vec3(1.0, 0.63, 0.0), vec3(1.0, 0.85, 0.3), smoothstep(0.8, 0.0, across));
            col += vec3(1.0) * smoothstep(0.95, 1.0, sin(vUv.x * 0.9 - uTime * 4.0));
        } else if (mode > 4.5 && mode < 5.5) {
            // Neon: pink and cyan sections running down, with a soft pulse.
            float seg = step(0.5, fract(vUv.x * 1.2 - uTime * 1.5));
            col = mix(vec3(1.0, 0.31, 0.85), vec3(0.25, 0.96, 1.0), seg) * (1.0 + 0.25 * sin(uTime * 4.0));
        } else if (mode > 5.5) {
            // Polished silver with a running glint.
            col = mix(vec3(0.62, 0.68, 0.72), vec3(0.90, 0.93, 0.95), smoothstep(0.9, 0.0, across));
            col += vec3(1.0) * smoothstep(0.97, 1.0, sin(vUv.x * 0.7 - uTime * 5.0));
        }
        else if (mode < 0.5) {
            // The classic monofilament: a bright core and a glint running down it.
            col = mix(col * 0.82, col, smoothstep(0.9, 0.1, across));
            col += vec3(1.0) * smoothstep(0.97, 1.0, sin(vUv.x * 0.8 - uTime * 3.0)) * 0.55;
        }
        // Modes 2 (rainbow) and 4 (ocean) use the vertex colour as is.
    }
    a *= vColor.a * (1.0 - vFog);
    if (a < 0.003) discard;
    fragColor = uAdditive > 0.5 ? vec4(col * a, 1.0) : vec4(col, a);
}
