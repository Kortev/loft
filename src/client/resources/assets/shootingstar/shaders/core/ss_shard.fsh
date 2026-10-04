#version 150

// A shard of the other universe: a window onto its sky wherever you look at it from, with a white-hot rim.

uniform float Time;
uniform vec3 Spin;

in vec3 relPos;
in vec3 worldNormal;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// ---- the other universe: nebulae, stars and a few galaxies, by view direction ----

float c_hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float c_noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(c_hash(i), c_hash(i + vec3(1, 0, 0)), f.x), mix(c_hash(i + vec3(0, 1, 0)), c_hash(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(c_hash(i + vec3(0, 0, 1)), c_hash(i + vec3(1, 0, 1)), f.x), mix(c_hash(i + vec3(0, 1, 1)), c_hash(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}

float c_fbm(vec3 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * c_noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

vec3 c_galaxy(vec3 d, vec3 centre, vec3 tint, float size, float spin) {
    float cd = dot(d, centre);
    if (cd < 0.85) {
        return vec3(0.0);
    }
    vec3 u = normalize(cross(centre, vec3(0.31, 0.12, 0.94)));
    vec3 v = cross(centre, u);
    vec2 p = vec2(dot(d, u), dot(d, v) * 1.8) / size;
    float r = length(p);
    float a = atan(p.y, p.x);
    float arms = pow(0.5 + 0.5 * cos(2.0 * (a - log(r + 0.04) * 2.6 - spin)), 4.0);
    float core = exp(-r * r * 60.0);
    float disk = exp(-r * 2.6) * smoothstep(1.0, 0.15, r);
    return tint * (core * 4.0 + disk * arms * 1.6 + disk * 0.15);
}

vec3 cosmos(vec3 d, float t) {
    vec3 q = d * 2.1 + vec3(0.0, t * 0.004, t * 0.002);
    float warp = c_fbm(q * 1.6 + 4.0);
    float n1 = c_fbm(q + warp * 1.4);
    float n2 = c_fbm(q * 2.2 + warp * 0.8 + 7.1);
    vec3 col = vec3(0.015, 0.0, 0.04);
    col += vec3(0.85, 0.1, 0.9) * pow(n1, 3.0) * 3.0;
    col += vec3(0.05, 0.55, 1.0) * pow(n2, 3.2) * 2.8;
    col += vec3(1.0, 0.55, 0.15) * pow(n1 * n2 * 1.6, 4.0) * 7.0;
    col *= 0.45 + 0.55 * smoothstep(0.32, 0.62, c_fbm(q * 3.4 + 3.3));
    vec3 sp = d * 260.0;
    vec3 cell = floor(sp);
    float h = c_hash(cell);
    if (h > 0.982) {
        vec3 f = fract(sp) - 0.5;
        col += vec3(1.0, 0.95, 0.9) * smoothstep(0.42, 0.0, length(f)) * (h - 0.982) * 90.0;
    }
    col += c_galaxy(d, normalize(vec3(0.6, 0.65, 0.3)), vec3(1.0, 0.82, 0.62), 0.22, t * 0.01);
    col += c_galaxy(d, normalize(vec3(-0.7, 0.45, 0.5)), vec3(0.65, 0.8, 1.0), 0.16, t * 0.013);
    col += c_galaxy(d, normalize(vec3(0.1, 0.85, -0.5)), vec3(1.0, 0.6, 0.95), 0.12, t * 0.017);
    col += c_galaxy(d, normalize(vec3(-0.2, 0.3, -0.9)), vec3(0.8, 1.0, 0.85), 0.1, t * 0.011);
    return col;
}

void main() {
    vec3 view = normalize(relPos);
    vec3 n = normalize(worldNormal);
    if (dot(n, view) > 0.0) {
        n = -n;
    }
    float facing = -dot(view, n);
    // A dark glass full of their night: looking into it, bent at each facet, the stars and dust of their universe
    // show deep inside, dimmer than the bright sky behind it, so the shard always stands out against that sky.
    vec3 d = normalize(refract(view, n, 0.7) + Spin);
    vec3 c = vec3(0.012, 0.004, 0.03) + cosmos(d * 1.3, Time) * 0.32;
    // Each facet catches the light of the broken sky differently, so the facets read as planes.
    vec3 r = reflect(view, n);
    float sky = 0.5 + 0.5 * r.y;
    c += vec3(0.35, 0.22, 0.6) * pow(sky, 3.0) * 0.35;
    c += vec3(1.0, 0.85, 1.0) * pow(max(dot(r, normalize(vec3(0.25, 0.9, 0.35))), 0.0), 24.0) * 0.9;
    // A cold rim where the facets turn away.
    c += vec3(0.45, 0.8, 1.0) * pow(1.0 - facing, 4.0) * 1.1;
    // The facet edges burn: a hard white hairline with a violet glow inside it (the texture coordinates are
    // barycentric).
    vec3 b = vec3(texCoord0, 1.0 - texCoord0.x - texCoord0.y);
    float e = min(min(b.x, b.y), b.z);
    float px = max(fwidth(e), 1.0e-5);
    c += vec3(0.9, 1.0, 1.0) * (1.0 - smoothstep(0.0, px * 1.5, e)) * 1.3;
    c += vec3(0.75, 0.4, 1.0) * exp(-e / max(px * 10.0, 0.035)) * 0.45;
    fragColor = vec4(min(c, vec3(1.0)), 1.0);
}
