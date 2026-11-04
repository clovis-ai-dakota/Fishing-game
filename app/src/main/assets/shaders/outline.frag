#version 300 es
precision highp float;

#include "common.glsl"

uniform vec3 uOutlineColor;
uniform vec3 uFogColor;
uniform float uOpacity;
in float vDist;
out vec4 fragColor;

void main() {
    float fog = fogAmount(vDist);
    fragColor = vec4(mix(uOutlineColor, uFogColor, fog), uOpacity * (1.0 - fog * 0.7));
}
