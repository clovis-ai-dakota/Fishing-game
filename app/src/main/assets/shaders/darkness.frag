#version 300 es
precision highp float;

#include "common.glsl"

// Darkness below 300 m: the same radial falloff around the hook as the 2D renderer, so the
// visible area is exactly the gameplay one. uMode 1 draws the Lantern's warm glow instead.
uniform vec2 uHookScreen;     // pixels, origin bottom-left
uniform float uPxPerMeter;
uniform float uVisibleRadius; // meters
uniform float uDarkness;      // 0..1
uniform float uMode;
in vec2 vNdc;
out vec4 fragColor;

void main() {
    float d = length(gl_FragCoord.xy - uHookScreen) / (uVisibleRadius * uPxPerMeter);
    if (uMode > 0.5) {
        float g = clamp(1.0 - d, 0.0, 1.0);
        fragColor = vec4(vec3(1.0, 0.88, 0.51) * g * 0.25, 1.0);
        return;
    }
    float a = d < 0.6 ? mix(0.0, 0.45, d / 0.6) : (d < 1.0 ? mix(0.45, 0.97, (d - 0.6) / 0.4) : 0.97);
    fragColor = vec4(0.0, 0.0, 0.0, a * uDarkness);
}
