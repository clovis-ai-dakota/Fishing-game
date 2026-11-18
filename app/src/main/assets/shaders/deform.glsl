// Animation done on the GPU: swimming, flapping, swaying. [p] is the vertex in model space.
uniform vec4 uSwim; // x: swim phase, y: amplitude, z: per-object seed, w: unused
uniform float uMaterial;

vec3 deform(vec3 p, float part, vec2 uv) {
    if (uMaterial < 0.5) {
        // Fish (unit space, nose at +x): a travelling wave down the body, strongest at the tail.
        float tailW = smoothstep(0.28, -0.55, p.x);
        p.z += sin(uSwim.x - p.x * 6.0) * uSwim.y * tailW * 0.09;
        if (isPart(part, 5.0)) p.z += sign(p.z) * (0.5 + 0.5 * sin(uTime * 9.0 + uSwim.z)) * 0.035;
        if (isPart(part, 6.0)) {
            float t = clamp((p.x - 0.44) / 0.16, 0.0, 1.0);
            p.y += sin(uTime * 5.0 + p.x * 25.0 + p.y * 40.0 + uSwim.z) * 0.03 * t;
        }
    } else if (uMaterial > 4.5 && uMaterial < 5.5) {
        // The village's flags flutter, more towards their tip (u along the cloth).
        if (isPart(part, 63.0) || isPart(part, 65.0)) p.z += sin(uTime * 4.0 + uv.x * 5.0 + p.x * 0.7) * 0.22 * uv.x;
        // Leaves sway in the breeze (or the water), more the higher they are.
        if (isPart(part, 67.0)) {
            float k = 0.035 * clamp(p.y * 0.25, 0.2, 1.0);
            p.x += sin(uTime * 1.7 + p.x * 0.9 + p.z * 0.6) * k;
            p.z += cos(uTime * 1.3 + p.x * 0.7) * k;
        }
    } else if (uMaterial < 1.5) {
        // The hook doesn't move.
    } else if (uMaterial < 2.5) {
        if (isPart(part, 23.0)) {
            p.x += sin(uTime * 2.5 + uv.x * 5.0 + uSwim.z) * 0.16 * uv.x;
            p.z += cos(uTime * 2.0 + uv.x * 4.0 + uSwim.z) * 0.10 * uv.x;
        }
        if (isPart(part, 22.0) || isPart(part, 23.0)) {
            float pulse = 1.0 + 0.05 * sin(uTime * 2.2 + uSwim.z);
            p.xz *= pulse;
        }
    } else if (uMaterial < 3.5) {
        if (isPart(part, 35.0)) p.z += sin(uTime * 3.0 + p.x * 3.0) * 0.18 * uv.x;
    } else {
        if (isPart(part, 40.0)) p.x += sin(uTime * 1.1 + p.y * 0.35 + uSwim.z) * 0.35 * clamp(p.y / 10.0, 0.0, 1.5);
    }
    return p;
}
