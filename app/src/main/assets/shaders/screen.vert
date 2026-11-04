#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
out vec2 vNdc;
void main() {
    vNdc = aPos.xy;
    gl_Position = vec4(aPos.xy, 0.0, 1.0);
}
