#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
layout(location = 3) in float aPart;

#include "common.glsl"
#include "deform.glsl"

uniform mat4 uModel;
uniform mat3 uNormalMatrix;
uniform float uOutlineWidth; // pixels

out float vDist;

void main() {
    vec3 p = deform(aPos, aPart, aUv);
    vec4 world = uModel * vec4(p, 1.0);
    vec4 clip = uProj * uView * world;
    vec3 n = normalize(mat3(uView) * (uNormalMatrix * aNormal));
    vec2 dir = n.xy;
    float l = length(dir);
    dir = l > 1e-4 ? dir / l : vec2(0.0);
    // Inverted hull: push the back faces out by a constant number of pixels.
    clip.xy += dir * uOutlineWidth * 2.0 / uResolution * clip.w;
    vDist = length(world.xyz - uCamPos);
    gl_Position = clip;
}
