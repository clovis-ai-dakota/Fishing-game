#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
layout(location = 3) in float aPart;
layout(location = 4) in vec4 aColor;

#include "common.glsl"
#include "deform.glsl"

uniform mat4 uModel;
uniform mat3 uNormalMatrix;

out vec3 vWorld;
out vec3 vNormal;
out vec3 vLocal;
out vec3 vLocalNormal;
out vec2 vUv;
out float vPart;
out vec3 vViewNormal;

void main() {
    vec3 p = deform(aPos, aPart, aUv);
    vec4 world = uModel * vec4(p, 1.0);
    vWorld = world.xyz;
    vNormal = normalize(uNormalMatrix * aNormal);
    vViewNormal = normalize(mat3(uView) * vNormal);
    vLocal = aPos;
    vLocalNormal = aNormal;
    vUv = aUv;
    vPart = aPart;
    gl_Position = uProj * uView * world;
}
