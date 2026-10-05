#version 150

// Another universe, by the block. Mode 0 marches the inside of a cube from -1 to 1: a nebula, a few galaxies and
// stars in front of the far sky of that universe, with the cube's edges glowing. Mode 1 is the window of an open
// gate (a quad from -1 to 1 in x and y): the same universe seen through it, marched into the space behind, opening
// from the middle as a square. Mode 2 is a cheap cube that only looks through to that universe's sky.

uniform float Time;
uniform int Mode;
uniform float Seed;
uniform float Edge;
uniform float Heat;
uniform float Fade;
uniform float Reveal;
uniform int Steps;

in vec3 objPos;
in vec3 objNormal;
in vec3 camObj;
in vec3 viewPos;

out vec4 fragColor;

const float PI = 3.14159265;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

vec3 hash3(vec3 p) {
    return vec3(hash(p), hash(p + vec3(7.31, 1.7, 3.3)), hash(p + vec3(2.9, 8.13, 5.7)));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += noise(p) * a;
        p = p * 2.07 + vec3(3.1, 1.7, 4.9);
        a *= 0.5;
    }
    return s;
}

// The colours of that universe: violet and magenta gas, teal where it is thin, gold where it is dense.
vec3 gas(float t, float k) {
    vec3 c = mix(vec3(0.32, 0.12, 0.85), vec3(0.95, 0.22, 0.62), smoothstep(0.2, 0.75, t));
    c = mix(c, vec3(0.12, 0.75, 0.95), smoothstep(0.55, 0.9, k) * 0.7);
    return mix(c, vec3(1.0, 0.72, 0.38), smoothstep(0.82, 1.0, t) * 0.6);
}

// A spiral galaxy where the ray crosses its plane, worked out exactly rather than marched (the disc is far thinner
// than a step): its centre, pole, size and turn. Brighter edge-on, where the ray sees more of it, and a round glow
// at the core from wherever it is seen.
vec3 galaxy(vec3 ro, vec3 rd, float t0, float t1, vec3 c, vec3 pole, float size, float turn, vec3 tint) {
    vec3 col = vec3(0.0);
    float along = dot(c - ro, rd);
    if (along > t0 && along < t1) {
        float miss = length(ro + rd * along - c) / size;
        col += vec3(1.0, 0.86, 0.68) * (exp(-miss * miss * 300.0) * 3.0 + exp(-miss * miss * 30.0) * 0.25);
    }
    float cosine = dot(rd, pole);
    float t = dot(c - ro, pole) / (abs(cosine) < 1.0e-4 ? 1.0e-4 : cosine);
    if (t < t0 || t > t1) {
        return col;
    }
    vec3 d = (ro + rd * t - c) / size;
    float r = length(d);
    if (r > 1.1) {
        return col;
    }
    vec3 ax = normalize(cross(pole, abs(pole.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 ay = cross(pole, ax);
    float a = atan(dot(d, ay), dot(d, ax));
    float swirl = a - log(r + 0.03) * 2.3 - turn;
    float arms = pow(0.5 + 0.5 * cos(2.0 * swirl), 3.0);
    float fine = 0.65 + 0.35 * noise(vec3(r * 22.0, swirl * 4.0, size * 9.0));
    float disc = exp(-r * 2.7) * (1.0 - smoothstep(0.75, 1.1, r));
    float knots = smoothstep(0.72, 0.95, noise(vec3(r * 18.0, swirl * 6.0, turn + size * 10.0))) * arms;
    float lanes = 1.0 - 0.6 * smoothstep(0.55, 0.8, noise(vec3(r * 30.0, swirl * 5.0, 4.0))) * (1.0 - arms);
    vec3 light = tint * disc * (0.12 + 1.1 * arms * fine) * lanes + vec3(1.0, 0.4, 0.75) * knots * disc * 1.8;
    light += vec3(1.0, 0.85, 0.6) * exp(-r * r * 40.0) * 1.2;
    return col + light * 1.6 / max(abs(cosine), 0.12);
}

// That universe's far sky, by direction: faint gas, a haze of stars too far to resolve, and pinpoint stars.
vec3 sky(vec3 d, float px) {
    float n = fbm(d * 2.2 + Seed);
    float m = fbm(d * 4.5 - Seed * 0.7 + vec3(4.0));
    float ridge = 1.0 - abs(2.0 * fbm(d * 3.1 + vec3(Seed * 1.3, 2.0, 0.0)) - 1.0);
    vec3 c = vec3(0.012, 0.006, 0.026);
    c += gas(n, m) * pow(smoothstep(0.45, 0.85, n), 2.0) * 0.18;
    c += gas(m, n) * pow(ridge, 8.0) * 0.12;
    c += vec3(0.18, 0.14, 0.3) * pow(m, 4.0) * 0.12;
    // Stars on a grid of directions, each cell at most one, sized to a pixel or so.
    float scale = 220.0;
    vec3 g = floor(d * scale);
    float s = hash(g + Seed);
    vec3 at = (g + 0.2 + 0.6 * hash3(g)) / scale;
    float r = length(d - normalize(at)) / max(px, 0.0015);
    float bright = step(0.9, s) * (0.4 + 3.0 * pow(max(s - 0.9, 0.0) * 10.0, 3.0));
    c += mix(vec3(0.7, 0.8, 1.0), vec3(1.0, 0.8, 0.6), hash(g * 1.3)) * exp(-r * r * 1.4) * bright;
    return c;
}

float hit(vec3 ro, vec3 rd, vec3 lo, vec3 hi, out float t0, out float t1) {
    vec3 inv = 1.0 / rd;
    vec3 a = (lo - ro) * inv;
    vec3 b = (hi - ro) * inv;
    vec3 mn = min(a, b);
    vec3 mx = max(a, b);
    t0 = max(max(mn.x, mn.y), mn.z);
    t1 = min(min(mx.x, mx.y), mx.z);
    return t1 > max(t0, 0.0) ? 1.0 : 0.0;
}

// The three galaxies of the block, wherever Seed puts them.
vec3 galaxies(vec3 o, vec3 rd, float t0, float t1, float turn) {
    vec3 jitter = (hash3(vec3(Seed, 1.0, 2.0)) - 0.5) * 0.5 * step(0.001, abs(Seed));
    vec3 g = galaxy(o, rd, t0, t1, vec3(0.12, 0.05, -0.12) + jitter, normalize(vec3(0.45, 1.0, 0.55) + jitter), 0.85, turn,
        vec3(0.55, 0.7, 1.0));
    g += galaxy(o, rd, t0, t1, vec3(-0.6, -0.5, 0.55) - jitter, normalize(vec3(-0.6, 0.5, 0.6)), 0.3, turn * 1.3 + 1.0,
        vec3(1.0, 0.7, 0.95));
    g += galaxy(o, rd, t0, t1, vec3(0.55, 0.62, 0.6), normalize(vec3(0.1, 0.3, -1.0) - jitter), 0.2, turn * 1.7 + 2.0,
        vec3(0.7, 1.0, 0.9));
    return g;
}

// The light along the ray from t0 to t1 through the universe's near space, in front of its sky. {@code px} is
// the size of a pixel at the surface, which keeps the stars to a pixel or two.
vec3 march(vec3 ro, vec3 rd, float t0, float t1, int steps, vec3 shift, float px) {
    float dt = (t1 - t0) / float(steps);
    float t = t0 + dt * hash(vec3(gl_FragCoord.xy, Seed));
    vec3 light = vec3(0.0);
    float through = 1.0;
    float turn = Time * 0.004;
    float starPx = max(px, 0.0008);
    for (int i = 0; i < 96; i++) {
        if (i >= steps) {
            break;
        }
        vec3 p = ro + rd * t + shift;
        float n = fbm(p * 1.5 + vec3(Seed, 0.0, Time * 0.002));
        float k = noise(p * 3.7 - vec3(0.0, Time * 0.003, Seed));
        float ridge = 1.0 - abs(2.0 * fbm(p * 2.3 + vec3(2.0, Seed, 1.0)) - 1.0);
        float dens = pow(smoothstep(0.52, 0.86, n), 2.5) * 0.9 + pow(ridge, 14.0) * smoothstep(0.35, 0.6, n) * 1.4;
        float dust = smoothstep(0.55, 0.75, k) * smoothstep(0.4, 0.65, n) * 3.0;
        vec3 e = gas(n, k) * dens;
        // Stars: one at most in each cell, counted at the step that passes closest to it.
        vec3 cell = floor(p * 10.0);
        float h = hash(cell + Seed * 3.0);
        if (h > 0.95) {
            vec3 sp = (cell + 0.2 + 0.6 * hash3(cell)) / 10.0 - shift;
            float along = dot(sp - ro, rd);
            if (along >= t - dt * 0.5 && along < t + dt * 0.5) {
                float size = starPx * max(along, 0.05) / max(t0, 0.05);
                float miss = length(ro + rd * along - sp) / size;
                float bright = 0.25 + 8.0 * pow(max(h - 0.95, 0.0) / 0.05, 6.0);
                light += through * mix(vec3(0.7, 0.8, 1.0), vec3(1.0, 0.82, 0.62), hash(cell * 1.7)) * exp(-miss * miss * 1.2) * bright;
            }
        }
        light += through * e * dt;
        through *= exp(-dust * dt);
        t += dt;
    }
    return light + galaxies(ro + shift, rd, t0, t1, turn) * (0.4 + 0.6 * through) + through * sky(rd, px * 0.8);
}

// How close a point on a cube face is to the face's edges (0 on an edge).
float edgeDistance(vec3 p) {
    vec3 a = abs(p);
    float m = max(a.x, max(a.y, a.z));
    // Drop the coordinate the face is perpendicular to; what is left says how far in from the edges.
    vec3 q = a;
    if (a.x == m) {
        q.x = 0.0;
    } else if (a.y == m) {
        q.y = 0.0;
    } else {
        q.z = 0.0;
    }
    return 1.0 - max(q.x, max(q.y, q.z));
}

vec3 edges(vec3 p, float px, float strength) {
    float e = max(edgeDistance(p), 0.0);
    float line = 1.0 - smoothstep(px * 0.8, px * 2.0, e);
    float halo = exp(-e / max(px * 6.0, 0.02));
    return vec3(0.85, 0.8, 1.0) * (line * 2.4 + halo * 0.6) * strength;
}

void main() {
    vec3 ro = camObj;
    vec3 rd = normalize(objPos - camObj);
    float px = length(fwidth(objPos));
    vec3 c;
    if (Mode == 1) {
        // The window: the quad is the plane z = 0; that universe lies on the far side from the eye.
        float side = camObj.z >= 0.0 ? 1.0 : -1.0;
        vec2 q = objPos.xy;
        float open = max(abs(q.x), abs(q.y));
        float edgeOpen = Reveal;
        if (open > edgeOpen) {
            discard;
        }
        vec3 o = vec3(ro.xy, ro.z * side);
        vec3 d = vec3(rd.xy, rd.z * side);
        float t0 = -o.z / d.z;
        float t1 = t0 + 3.0 / max(abs(d.z), 0.25);
        c = march(o, d, t0, t1, Steps, vec3(0.0, 0.0, 1.0), px);
        // The rim of the opening burns white and crackles as it grows.
        float rim = edgeOpen - open;
        float crackle = 0.6 + 0.4 * noise(vec3(q * 18.0, Time * 0.6));
        c += vec3(0.9, 0.85, 1.0) * exp(-rim / max(px * 3.0, 0.004)) * 3.0 * crackle;
        c += vec3(0.7, 0.55, 1.0) * exp(-rim / 0.05) * 0.8;
    } else {
        float t0;
        float t1;
        // The fragment is on the cube, so the ray meets it; along the silhouette rounding can say otherwise.
        float onFace = length(objPos - ro);
        if (hit(ro, rd, vec3(-1.0), vec3(1.0), t0, t1) < 0.5) {
            t0 = onFace;
            t1 = onFace;
        }
        t0 = max(t0, 0.0);
        if (Mode == 2) {
            c = sky(rd, px * 2.0) * 2.0 + gas(fbm(rd * 1.7 + Seed), 0.5) * 0.08 + galaxies(ro, rd, t0, t1, Time * 0.004);
        } else {
            c = march(ro, rd, t0, t1, Steps, vec3(0.0), px);
        }
        vec3 front = ro + rd * t0;
        vec3 back = ro + rd * t1;
        c += edges(front, px, Edge);
        // The far edges show through the dark of that space, so the block reads as a block.
        c += edges(back, px, Edge * 0.45);
        // A glassy sheen on the faces.
        vec3 n = normalize(objNormal);
        float facing = abs(dot(n, rd));
        c += vec3(0.55, 0.5, 0.8) * pow(1.0 - facing, 4.0) * 0.35 * Edge;
    }
    c = mix(c, vec3(1.0, 0.97, 1.0) * 3.0, clamp(Heat, 0.0, 1.0));
    fragColor = vec4(c * Fade, 1.0);
}
