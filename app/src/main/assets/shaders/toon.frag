#version 300 es
precision highp float;

#include "common.glsl"

uniform float uMaterial;
uniform vec3 uBase;       // main colour (fish body, rock, wood...)
uniform vec3 uAccent;     // second colour (fish accent, hull bottom...)
uniform vec3 uExtra;      // third colour (boat trim, coral tint...)
uniform vec3 uExtra2;     // fourth colour (boat cabin, sail)
uniform vec4 uFlags;      // x: sparkling / touched, y: silhouette (1 unknown, 2 seen), z: pattern, w: skin mode
uniform vec4 uFish;       // x: height ratio, y: gill x, z: cuteness, w: menace
uniform vec4 uFish2;      // x, y: eye position (unit space), z: eye radius, w: photophores
uniform vec4 uEye;        // rgb: iris colour, w: style (0 cute, 1 normal, 2 glowing, 3 slit)
uniform vec4 uBoatMode;   // x: trim mode, y: sail mode, z: hull pattern, w: custom flag
uniform vec3 uSailColor;
uniform vec3 uFlagColor;
uniform vec3 uLightDir;
uniform vec3 uLightColor;
uniform vec3 uAmbient;
uniform vec3 uFogColor;
uniform vec3 uWaterTint;
uniform float uOpacity;

in vec3 vWorld;
in vec3 vNormal;
in vec3 vLocal;
in vec3 vLocalNormal;
in vec2 vUv;
in float vPart;
in vec3 vViewNormal;
out vec4 fragColor;

struct Surface {
    vec3 albedo;
    float spec;      // 0..1 highlight strength
    float rim;       // 0..1 rim light strength
    vec3 rimColor;
    float emissive;  // 0..1: unlit, glowing
    float alpha;
    float shadowCool;
};

Surface surface(vec3 albedo) {
    Surface s;
    s.albedo = albedo; s.spec = 0.35; s.rim = 0.5; s.rimColor = vec3(0.75, 0.92, 1.0);
    s.emissive = 0.0; s.alpha = 1.0; s.shadowCool = 1.0;
    return s;
}

// ---- Fish --------------------------------------------------------------------------------

Surface fishSurface() {
    float h = max(uFish.x, 0.05);
    vec3 base = uBase;
    vec3 accent = uAccent;
    Surface s = surface(base);
    if (isPart(vPart, 2.0)) {
        // Anime eye, facing the camera. Cute: huge iris and sparkles. Normal: smaller.
        // Glowing: a luminous iris. Slit: a narrow pupil in a burning iris, on a dark eyeball.
        vec2 e = vViewNormal.xy;
        float style = uEye.w;
        vec3 irisCol = uEye.rgb;
        vec3 col = style > 2.5 ? mix(vec3(0.08, 0.04, 0.05), irisCol * 0.25, 0.3) : vec3(0.98);
        float irisR = style < 0.5 ? 0.78 : (style < 1.5 ? 0.66 : 0.7);
        float iris = length(e - vec2(0.04, -0.02));
        vec3 irisTop = mix(irisCol, vec3(0.02, 0.03, 0.08), 0.55);
        vec3 irisBottom = mix(irisCol, vec3(1.0), 0.3);
        float glow = 0.35;
        if (iris < irisR) col = mix(irisTop, irisBottom, smoothstep(0.5, -0.6, e.y));
        if (iris < irisR && iris > irisR - 0.08) col *= 0.55;
        if (style > 1.5) {
            // Luminous iris: bright core.
            if (iris < irisR) col = mix(irisCol * 1.3, vec3(1.0, 0.95, 0.8), smoothstep(irisR, 0.0, iris) * 0.5);
            glow = 0.9;
        }
        if (style > 2.5) {
            // A vertical slit pupil.
            vec2 q = e - vec2(0.04, -0.02);
            if (abs(q.x) < 0.09 * (1.0 - q.y * q.y) && iris < irisR * 0.95) col = vec3(0.02, 0.0, 0.0);
        } else if (iris < irisR * 0.48) {
            col = vec3(0.02, 0.02, 0.05);
        }
        if (style < 2.5) {
            float big = style < 0.5 ? 0.26 : 0.18;
            if (length(e - vec2(-0.24, 0.30)) < big) col = vec3(1.0);
            if (length(e - vec2(0.26, -0.24)) < big * 0.42) col = vec3(1.0);
            if (style < 0.5 && length(e - vec2(0.05, 0.42)) < 0.07) col = vec3(1.0);
        }
        s = surface(col);
        s.spec = 0.0; s.rim = 0.0; s.emissive = glow;
        return s;
    }
    if (isPart(vPart, 3.0)) {
        s = surface(mix(uExtra, vec3(1.0, 0.98, 0.85), 0.55));
        s.emissive = 1.0;
        return s;
    }
    if (isPart(vPart, 4.0)) {
        s = surface(mix(vec3(0.97, 0.96, 0.9), vec3(0.85, 0.8, 0.6), uFish.w));
        s.rim = 0.0;
        return s;
    }
    bool fin = isPart(vPart, 1.0) || isPart(vPart, 5.0) || isPart(vPart, 7.0);
    if (fin) {
        vec3 finCol = mix(mix(accent, base, 0.3), vec3(1.0), 0.15);
        // Fin rays.
        float rays = 0.94 + 0.06 * sin(atan(vLocal.y, vLocal.x + 0.2) * 60.0);
        finCol *= rays;
        // Lighter, translucent-looking edges.
        finCol = mix(finCol, vec3(1.0), 0.25 * smoothstep(0.6, 1.0, max(abs(vUv.x - 0.5), abs(vUv.y - 0.5)) * 2.0));
        s = surface(finCol);
        s.spec = 0.15;
        return s;
    }
    if (isPart(vPart, 6.0)) {
        s = surface(mix(base, accent, 0.3));
        return s;
    }
    // Body: darker back, pale belly (countershading), then the species' markings. Deep fish
    // have a darker, harsher back.
    float ny = vLocalNormal.y;
    float menace = uFish.w;
    vec3 back = mix(base, accent, 0.5) * mix(0.85, 0.45, menace);
    vec3 belly = mix(base, vec3(1.0), mix(0.55, 0.2, menace));
    vec3 col = ny > 0.0 ? mix(base, back, smoothstep(0.0, 0.9, ny)) : mix(base, belly, smoothstep(0.0, 0.8, -ny));
    float pattern = uFlags.z;
    float x = vLocal.x;
    float yh = vLocal.y / h;
    if (pattern > 0.5 && pattern < 1.5) {
        float band = smoothstep(0.42, 0.5, abs(fract(x * 8.0) - 0.5) * 2.0);
        if (x < 0.32 && x > -0.33) col = mix(col, accent * 0.7, 0.55 * (1.0 - band));
    } else if (pattern > 1.5 && pattern < 2.5) {
        float d = abs(ny);
        col = mix(col, mix(accent, vec3(1.0), 0.45), 0.7 * (1.0 - smoothstep(0.1, 0.2, d)) * step(-0.33, x));
    } else if (pattern > 2.5) {
        vec2 cell = vec2(x * 13.0, yh * 7.0);
        vec2 id = floor(cell);
        vec2 f = fract(cell) - 0.5;
        float r = 0.18 + 0.15 * hash21(id);
        float spot = 1.0 - smoothstep(r - 0.05, r, length(f + (vec2(hash21(id + 3.1), hash21(id + 7.7)) - 0.5) * 0.4));
        if (x < 0.36 && x > -0.32 && hash21(id + 1.3) > 0.35) col = mix(col, accent * 0.75, 0.65 * spot);
    }
    // Gill line.
    float gx = uFish.y - 0.07 * yh * yh * 4.0;
    if (abs(x - gx) < 0.007 && abs(yh) < 0.32 && abs(vLocalNormal.z) > 0.3) col = mix(col, accent * 0.5, 0.6);
    // Friendly shallow fish: rosy cheeks and a little smile.
    float cute = uFish.z;
    vec2 eyeP = uFish2.xy;
    float eyeR = uFish2.z;
    if (cute > 0.4 && abs(vLocalNormal.z) > 0.25) {
        vec2 c = vLocal.xy - (eyeP + vec2(-0.02, -eyeR * 1.55));
        float blush = 1.0 - smoothstep(0.75, 1.0, length(c / vec2(eyeR * 1.0, eyeR * 0.5)));
        col = mix(col, vec3(1.0, 0.55, 0.62), 0.45 * blush * cute);
        vec2 m = vLocal.xy - (eyeP + vec2(eyeR * 0.75, -eyeR * 1.35));
        float arc = abs(length(m * vec2(1.0, 1.6)) - eyeR * 0.55);
        if (arc < 0.006 && m.y < 0.0 && cute > 0.6) col = mix(col, vec3(0.25, 0.08, 0.12), 0.8);
    }
    s = surface(col);
    s.spec = mix(0.55, 0.85, menace);
    s.rim = 0.65;
    // Deep fish: a row of glowing photophores along the flank.
    if (uFish2.w > 0.5 && abs(vLocalNormal.z) > 0.3) {
        float rowY = -0.22 * h;
        vec2 q = vec2(fract((x + 0.3) / 0.07) - 0.5, (vLocal.y - rowY) / 0.035);
        if (x > -0.3 && x < 0.32 && length(q * vec2(0.07, 0.035) / 0.022) < 1.0) {
            s.albedo = mix(uExtra, vec3(1.0), 0.35);
            s.emissive = 1.0;
        }
    }
    return s;
}

// ---- Hook and bait -------------------------------------------------------------------------

Surface hookSurface() {
    float mode = uFlags.w; // 0 classic steel, 1 bone, 2 prism, 3 gold
    if (isPart(vPart, 11.0) || isPart(vPart, 12.0)) {
        vec3 pink = vec3(0.90, 0.45, 0.45);
        pink *= 0.88 + 0.12 * step(0.5, fract(vUv.x * 9.0));
        Surface s = surface(pink);
        s.spec = 0.45;
        return s;
    }
    if (isPart(vPart, 13.0)) {
        Surface s;
        if (mode > 0.5 && mode < 1.5) {
            float pulse = 0.5 + 0.5 * sin(uTime * 3.0);
            s = surface(mix(vec3(0.55, 0.43, 0.39), vec3(0.30, 0.82, 0.88), pulse));
            s.emissive = 0.4 + 0.6 * pulse;
        } else {
            s = surface(vec3(1.0, 0.80, 0.25));
            s.spec = 1.0;
            s.rimColor = vec3(1.0, 0.95, 0.7);
        }
        return s;
    }
    vec3 n = normalize(vNormal);
    Surface s;
    if (mode > 0.5 && mode < 1.5) {
        s = surface(mix(vec3(0.84, 0.80, 0.76), vec3(1.0, 0.98, 0.9), 0.5 + 0.5 * n.y));
        s.spec = 0.25;
    } else if (mode > 1.5 && mode < 2.5) {
        s = surface(hsv2rgb(vec3(fract(vUv.x * 1.5 + vWorld.y * 0.2 + uTime * 0.25), 0.55, 1.0)));
        s.spec = 1.0;
        s.rimColor = vec3(1.0);
        s.rim = 1.0;
        s.emissive = 0.25;
    } else if (mode > 2.5) {
        s = surface(mix(vec3(1.0, 0.63, 0.0), vec3(1.0, 0.92, 0.55), 0.5 + 0.5 * n.y));
        s.spec = 1.0;
        s.rimColor = vec3(1.0, 0.95, 0.7);
    } else {
        // Polished steel: a dark band and a bright band, like a chrome reflection.
        float env = smoothstep(-0.2, 0.2, n.y);
        s = surface(mix(vec3(0.42, 0.50, 0.55), vec3(0.93, 0.95, 0.97), env));
        s.spec = 1.0;
    }
    s.shadowCool = 0.6;
    return s;
}

// ---- Obstacles -----------------------------------------------------------------------------

Surface obstacleSurface() {
    vec3 n = normalize(vNormal);
    if (isPart(vPart, 21.0)) {
        float speck = noise2(vLocal.xy * 9.0 + vLocal.z * 4.0);
        vec3 col = mix(vec3(0.22, 0.28, 0.31), vec3(0.47, 0.56, 0.61), 0.4 + 0.6 * speck);
        // Moss and sponges on the top (only in the sunlit water: uAccent says how much).
        col = mix(col, uAccent, smoothstep(0.45, 0.8, n.y) * uBase.x);
        Surface s = surface(col);
        s.spec = 0.1;
        return s;
    }
    if (isPart(vPart, 20.0)) {
        float grain = 0.85 + 0.15 * sin(vUv.y * 6.2831 * 7.0 + sin(vUv.x * 30.0) * 0.6);
        vec3 col = mix(vec3(0.31, 0.20, 0.18), vec3(0.55, 0.43, 0.39), grain - 0.6);
        Surface s = surface(col * grain);
        s.spec = 0.1;
        return s;
    }
    if (isPart(vPart, 22.0) || isPart(vPart, 23.0)) {
        Surface s = surface(isPart(vPart, 22.0) ? vec3(0.96, 0.62, 0.78) : vec3(0.98, 0.75, 0.86));
        s.rim = 1.0;
        s.rimColor = vec3(1.0, 0.85, 0.95);
        s.alpha = isPart(vPart, 22.0) ? 0.72 : 0.6;
        s.emissive = 0.25;
        s.spec = 0.7;
        return s;
    }
    return surface(uBase);
}

// ---- Boat ----------------------------------------------------------------------------------

// The print on a hand-made boat's sail and flag (ClothPattern: 0 plain, 1 stripes, 2 stars,
// 3 waves), in the sail's uv space. Matches drawClothPrint in BoatArt.kt.
vec3 clothPrint(vec3 col, vec3 ink, float cloth) {
    if (cloth > 0.5 && cloth < 1.5) {
        if (fract(vUv.y * 5.0) < 0.35) col = mix(col, ink, 0.7);
    } else if (cloth > 1.5 && cloth < 2.5) {
        vec2 g = vec2(vUv.x * 5.0 + floor(vUv.y * 6.0) * 0.5, vUv.y * 6.0);
        vec2 f = fract(g) - 0.5;
        float a = atan(f.y, f.x);
        float r = 0.16 + 0.1 * cos(a * 5.0);
        if (length(f) < r) col = mix(col, ink, 0.7);
    } else if (cloth > 2.5) {
        float y = fract(vUv.y * 5.0 + 0.06 * sin(vUv.x * 18.0 + uTime * 1.5));
        if (abs(y - 0.5) < 0.09) col = mix(col, ink, 0.7);
    }
    return col;
}

Surface boatSurface() {
    if (isPart(vPart, 30.0)) {
        vec3 col = mix(uAccent, uBase, smoothstep(-0.4, 1.0, vLocal.y));
        // Hand-made hulls: 1 racing stripe, 2 two-tone, 3 waves, 4 polka dots, 5 checkers;
        // shop: 6 flames, 7 starry night, 8 metallic, 9 neon; Captain's Club: 10 aurora.
        float pattern = uBoatMode.z;
        vec2 q = vec2(vLocal.x, vLocal.y);
        if (pattern > 0.5 && pattern < 1.5) {
            if (abs(q.y - 0.42) < 0.12) col = uExtra;
        } else if (pattern > 1.5 && pattern < 2.5) {
            col = q.y > 0.3 ? uBase : uAccent;
        } else if (pattern > 2.5 && pattern < 3.5) {
            col = q.y > 0.3 + 0.12 * sin(q.x * 4.0) ? uBase : uAccent;
        } else if (pattern > 3.5 && pattern < 4.5) {
            vec2 cell = fract(vec2(q.x * 2.2, q.y * 2.6 + floor(q.x * 2.2) * 0.5)) - 0.5;
            if (length(cell) < 0.22) col = uExtra;
        } else if (pattern > 4.5 && pattern < 5.5) {
            float c = mod(floor(q.x * 2.0) + floor(q.y * 2.4 + 10.0), 2.0);
            col = c > 0.5 ? uBase : uAccent;
        } else if (pattern > 5.5 && pattern < 6.5) {
            // Flames licking up from the waterline, flickering.
            float tongue = -0.1 + 0.45 * (0.5 + 0.5 * sin(q.x * 5.0 + sin(q.x * 11.0) + uTime * 3.0));
            if (q.y < tongue) col = mix(uExtra, vec3(1.0, 0.95, 0.5), smoothstep(tongue, tongue - 0.35, q.y));
        } else if (pattern > 6.5 && pattern < 7.5) {
            // A night sky: twinkling stars over the base colour.
            vec2 cell = floor(vec2(q.x * 5.0, q.y * 6.0));
            vec2 f = fract(vec2(q.x * 5.0, q.y * 6.0)) - 0.5;
            float star = hash21(cell);
            if (star > 0.55 && length(f) < 0.09 + 0.05 * sin(uTime * 3.0 + star * 20.0)) col = vec3(1.0, 0.97, 0.85);
        } else if (pattern > 7.5 && pattern < 8.5) {
            // Polished metal: slanted sheen bands sliding along the hull.
            float band = fract((q.x + q.y * 0.6) * 0.35 - uTime * 0.25);
            col = mix(col, vec3(1.0), 0.45 * smoothstep(0.12, 0.0, abs(band - 0.5)));
        } else if (pattern > 8.5 && pattern < 9.5) {
            // Two pulsing neon tubes in the trim colour.
            float pulse = 0.65 + 0.35 * sin(uTime * 3.0);
            float tube = max(smoothstep(0.07, 0.0, abs(q.y - 0.45)), smoothstep(0.07, 0.0, abs(q.y - 0.8)));
            col = mix(col, uExtra * (1.0 + pulse * 0.5), tube * pulse);
        } else if (pattern > 9.5) {
            // Aurora curtains, green and violet, drifting along the hull.
            float w1 = smoothstep(0.1, 0.0, abs(q.y - 0.35 - 0.12 * sin(q.x * 2.2 + uTime * 1.4)));
            float w2 = smoothstep(0.1, 0.0, abs(q.y - 0.7 - 0.12 * sin(q.x * 2.2 + uTime * 1.4 + 1.0)));
            col = mix(col, vec3(0.39, 1.0, 0.85), w1 * 0.7);
            col = mix(col, vec3(0.70, 0.53, 1.0), w2 * 0.7);
        }
        Surface s = surface(col);
        s.spec = pattern > 7.5 && pattern < 8.5 ? 0.9 : 0.3;
        return s;
    }
    if (isPart(vPart, 31.0)) {
        float mode = uBoatMode.x;
        vec3 col = uExtra;
        if (mode > 0.5 && mode < 1.5) col = hsv2rgb(vec3(fract(vLocal.x * 0.2 + vLocal.y * 0.15 - uTime * 0.25), 0.7, 1.0));
        if (mode > 1.5) col = mix(vec3(1.0, 0.63, 0.0), vec3(1.0, 0.95, 0.7), 0.5 + 0.5 * sin(vLocal.x * 3.0 - uTime * 2.0));
        Surface s = surface(col);
        s.spec = mode > 0.5 ? 1.0 : 0.3;
        s.emissive = mode > 0.5 ? 0.2 : 0.0;
        return s;
    }
    if (isPart(vPart, 32.0)) return surface(uExtra2);
    if (isPart(vPart, 33.0)) {
        Surface s = surface(vec3(0.51, 0.83, 0.98));
        s.spec = 1.0;
        s.emissive = 0.2;
        return s;
    }
    if (isPart(vPart, 34.0)) {
        float mode = uBoatMode.y;
        vec3 col = vec3(0.56, 0.14, 0.67);
        if (mode < 0.5) col = hsv2rgb(vec3(fract(vLocal.y * 0.18 + uTime * 0.2), 0.7, 1.0));
        else if (mode < 1.5) col = mix(vec3(0.56, 0.14, 0.67), vec3(0.42, 0.11, 0.60), vUv.y);
        else col = clothPrint(uSailColor * (0.92 + 0.08 * step(0.5, fract(vUv.x * 4.0))), uExtra, uBoatMode.w - 1.0);
        Surface s = surface(col);
        s.spec = 0.25;
        return s;
    }
    if (isPart(vPart, 38.0)) {
        Surface s = surface(mix(uExtra, vec3(1.0), 0.15));
        s.spec = 0.8;
        return s;
    }
    if (isPart(vPart, 35.0) && uBoatMode.w > 0.5) {
        return surface(clothPrint(uFlagColor, mix(uFlagColor, vec3(1.0), 0.6), uBoatMode.w - 1.0));
    }
    if (isPart(vPart, 35.0)) {
        vec3 col = vec3(0.06, 0.13, 0.15);
        float edge = max(smoothstep(0.86, 0.95, vUv.x), max(smoothstep(0.12, 0.03, vUv.y), smoothstep(0.88, 0.97, vUv.y)));
        col = mix(col, vec3(0.30, 0.82, 0.88), edge);
        return surface(col);
    }
    if (isPart(vPart, 36.0)) return surface(vec3(0.36, 0.25, 0.22));
    if (isPart(vPart, 37.0)) return surface(mix(vec3(0.24, 0.15, 0.14), vec3(0.74, 0.67, 0.64), vUv.x));
    if (isPart(vPart, 20.0)) {
        float plank = 0.9 + 0.1 * step(0.5, fract(vLocal.z * 3.0));
        return surface(vec3(0.70, 0.52, 0.33) * plank);
    }
    if (isPart(vPart, 10.0)) return surface(vec3(0.69, 0.75, 0.77));
    return surface(uBase);
}

// ---- Decor ---------------------------------------------------------------------------------

Surface decorSurface() {
    if (isPart(vPart, 40.0)) {
        vec3 col = mix(vec3(0.20, 0.36, 0.12), vec3(0.52, 0.66, 0.22), clamp(vLocal.y / 14.0, 0.0, 1.0));
        return surface(col);
    }
    if (isPart(vPart, 42.0)) {
        Surface s = surface(mix(uBase, vec3(1.0), 0.2 * vUv.x));
        s.rim = 0.8;
        return s;
    }
    float speck = noise2(vLocal.xy * 3.0);
    Surface s = surface(mix(uBase * 0.7, uBase, speck));
    s.spec = 0.05;
    return s;
}

void main() {
    Surface s;
    if (uMaterial < 0.5) s = fishSurface();
    else if (uMaterial < 1.5) s = hookSurface();
    else if (uMaterial < 2.5) s = obstacleSurface();
    else if (uMaterial < 3.5) s = boatSurface();
    else s = decorSurface();

    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) n = -n;
    vec3 l = normalize(uLightDir);
    vec3 v = normalize(uCamPos - vWorld);

    // Cel shading: three flat tones with soft edges, cool-tinted shadows.
    float ndl = dot(n, l);
    float t1 = smoothstep(-0.10, -0.02, ndl);
    float t2 = smoothstep(0.42, 0.50, ndl);
    vec3 shadowCol = s.albedo * mix(vec3(1.0), vec3(0.60, 0.66, 0.92), s.shadowCool) * 0.62;
    vec3 lit = mix(mix(shadowCol, s.albedo * 0.86, t1), s.albedo * 1.05, t2);
    vec3 col = lit * uLightColor + s.albedo * uAmbient * 0.3;

    // Hard-edged highlight and rim light, the anime way.
    vec3 hv = normalize(l + v);
    col += s.spec * smoothstep(0.972, 0.985, dot(n, hv)) * uLightColor * 0.7;
    float fres = 1.0 - max(dot(n, v), 0.0);
    col += s.rim * smoothstep(0.62, 0.7, fres) * s.rimColor * 0.45 * (0.4 + 0.6 * uLightColor.x);

    // Sparkling fish: a gold rim and glints sweeping over the body. Touched obstacles: a red rim.
    if (uFlags.x > 0.5) {
        if (uMaterial < 0.5) {
            col += smoothstep(0.5, 0.7, fres) * vec3(1.0, 0.84, 0.31) * 0.9;
            float glint = smoothstep(0.96, 1.0, sin(vLocal.x * 28.0 - uTime * 5.0 + vLocal.y * 30.0));
            col += glint * vec3(1.0, 0.95, 0.6);
        } else {
            col = mix(col, vec3(1.0, 0.32, 0.32), 0.35 * smoothstep(0.4, 0.75, fres) + 0.12);
        }
    }

    col = mix(col, s.albedo, s.emissive);

    // Things under water take a little of its colour.
    if (vWorld.y < 0.0) col = mix(col, uWaterTint, 0.12);

    // Silhouettes: fish the Dex hasn't recorded yet (1 unknown, 2 seen), and the Nightmare
    // atmosphere's giant shadows (3).
    if (uFlags.y > 0.5) {
        vec3 dark = vec3(0.11, 0.18, 0.24);
        if (uFlags.y < 1.5) col = dark + 0.06 * smoothstep(0.6, 0.75, fres);
        else if (uFlags.y < 2.5) col = mix(dark, uBase * 0.6, 0.6) + 0.25 * smoothstep(0.6, 0.75, fres) * uBase;
        else col = vec3(0.03, 0.008, 0.012) + vec3(0.25, 0.02, 0.03) * smoothstep(0.6, 0.8, fres);
    }

    float fog = fogAmount(length(uCamPos - vWorld));
    col = mix(col, uFogColor, fog);
    fragColor = vec4(col, s.alpha * uOpacity);
}
