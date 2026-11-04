// Shared by every program: uniforms set once per frame, and helpers.
uniform mat4 uView;
uniform mat4 uProj;
uniform float uTime;
uniform vec2 uResolution;
uniform vec3 uCamPos;
uniform float uFogNear;
uniform float uFogFar;

const float M_FISH = 0.0;
const float M_HOOK = 1.0;
const float M_OBSTACLE = 2.0;
const float M_BOAT = 3.0;
const float M_DECOR = 4.0;

bool isPart(float part, float id) { return abs(part - id) < 0.5; }

// Water colour by depth (m), the same stops as the 2D renderer.
vec3 waterColor(float depth) {
    vec3 c0 = vec3(0.310, 0.765, 0.969); // 0 m
    vec3 c1 = vec3(0.008, 0.533, 0.820); // 100 m
    vec3 c2 = vec3(0.004, 0.224, 0.420); // 300 m
    vec3 c3 = vec3(0.024, 0.102, 0.227); // 500 m
    vec3 c4 = vec3(0.008, 0.024, 0.059); // 700 m
    float d = max(depth, 0.0);
    if (d < 100.0) return mix(c0, c1, d / 100.0);
    if (d < 300.0) return mix(c1, c2, (d - 100.0) / 200.0);
    if (d < 500.0) return mix(c2, c3, (d - 300.0) / 200.0);
    return mix(c3, c4, clamp((d - 500.0) / 200.0, 0.0, 1.0));
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), u.x), mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), u.x), u.y);
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

// How much of the water colour a point at view distance [dist] takes (fog).
float fogAmount(float dist) { return smoothstep(uFogNear, uFogFar, dist); }
