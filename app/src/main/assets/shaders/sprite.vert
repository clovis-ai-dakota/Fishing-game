#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
layout(location = 2) in vec2 aUv;
layout(location = 3) in float aPart;
layout(location = 4) in vec4 aColor;

#include "common.glsl"

out vec2 vUv;
out float vShape;
out vec4 vColor;
out float vFog;

void main() {
    vUv = aUv;
    vShape = aPart;
    vColor = aColor;
    vFog = fogAmount(length(aPos - uCamPos));
    gl_Position = uProj * uView * vec4(aPos, 1.0);
}
