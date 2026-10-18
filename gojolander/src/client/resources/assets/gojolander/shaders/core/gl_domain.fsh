#version 150

// Unlimited Void's full-screen pass over the world: the ring of ink spreading over the ground round the caster, the
// black flowing up out of it into a sphere and closing overhead, the void inside it (endless space, light streaming
// past, a vast black eye ringed with light, and still black water underfoot giving it all back), and the barrier
// cracking and shattering at the end. Positions are relative to the middle of the Domain.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 InvViewProj;
uniform vec2 ScreenSize;
uniform float Time;
// The camera, relative to the middle of the Domain.
uniform vec3 CamOffset;
uniform float Radius;
// How far the ring has spread (0 to 1), how far the black has climbed from it to the top (0 to 1), how much of the
// world inside is gone into the void (0 to 1), and how far the barrier has got through shattering (0 to 1).
uniform float Ring;
uniform float Rise;
uniform float Void;
uniform float Shatter;
// 1 when the camera is inside the sphere.
uniform float Inside;
// The way the great eye in the void lies, a unit vector.
uniform vec3 Eye;

in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.14159265;

float hash(float n) {
    return fract(sin(n * 12.9898 + 4.1414) * 43758.5453);
}

float hash3(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    return vec3(hash3(p), hash3(p + 17.31), hash3(p + 41.7));
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), f.x), mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), f.x), mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}

float fbm(vec3 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

vec3 scene(vec2 uv) {
    return texture(Sampler0, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

float rawDepth(vec2 uv) {
    return texture(Sampler1, clamp(uv, vec2(0.001), vec2(0.999))).r;
}

vec3 viewDir(vec2 uv) {
    vec4 far = InvViewProj * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    return normalize(far.xyz / far.w);
}

// The surface at uv, relative to the camera; the sky as very far off.
vec3 relAt(vec2 uv) {
    float d = rawDepth(uv);
    vec2 ndc = uv * 2.0 - 1.0;
    if (d >= 0.99999) {
        return viewDir(uv) * 100000.0;
    }
    vec4 w = InvViewProj * vec4(ndc, d * 2.0 - 1.0, 1.0);
    return w.xyz / w.w;
}

// The surface's facing at uv, from its neighbours (whichever side is nearer, so edges do not smear).
vec3 normalAt(vec2 uv, vec3 rel) {
    vec2 px = 1.0 / ScreenSize;
    vec3 r1 = relAt(uv + vec2(px.x, 0.0));
    vec3 l1 = relAt(uv - vec2(px.x, 0.0));
    vec3 u1 = relAt(uv + vec2(0.0, px.y));
    vec3 d1 = relAt(uv - vec2(0.0, px.y));
    vec3 dx = length(r1 - rel) < length(rel - l1) ? r1 - rel : rel - l1;
    vec3 dy = length(u1 - rel) < length(rel - d1) ? u1 - rel : rel - d1;
    vec3 n = normalize(cross(dx, dy));
    return dot(n, rel) > 0.0 ? -n : n;
}

// ---- the void ---------------------------------------------------------------------------------------------------

// Stars on a lattice of directions: in each cell maybe one, a point of some colour and brightness.
vec3 stars(vec3 d, float scale, float density) {
    vec3 p = d * scale;
    vec3 cell = floor(p);
    vec3 c = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        vec3 o = cell + vec3(float(i), 0.0, 0.0);
        vec3 h = hash33(o);
        if (h.x > density) {
            continue;
        }
        vec3 star = o + h;
        float dist = length(p - star);
        float size = 0.03 + 0.05 * h.y;
        float b = smoothstep(size, 0.0, dist) * (0.4 + 1.6 * h.z * h.z);
        float twinkle = 0.75 + 0.25 * sin(Time * (1.0 + 3.0 * h.y) + h.z * 40.0);
        vec3 tint = mix(vec3(0.7, 0.82, 1.0), mix(vec3(1.0, 0.85, 0.7), vec3(0.85, 0.7, 1.0), h.y), h.z);
        c += tint * b * twinkle;
    }
    return c;
}

// Light streaming past, all one way, towards the eye: long thin threads of it, some brighter, some faint.
float streams(vec3 d) {
    vec3 a = normalize(cross(Eye, vec3(0.0, 1.0, 0.0)) + vec3(1.0e-4));
    vec3 b = cross(a, Eye);
    float along = dot(d, Eye);
    float ang = atan(dot(d, b), dot(d, a));
    float lane = ang * 70.0;
    float id = floor(lane);
    float f = fract(lane);
    float thread = smoothstep(0.5, 0.0, abs(f - 0.5) * (1.4 + 3.0 * hash(id)));
    float run = fract(along * (2.0 + 2.0 * hash(id + 3.0)) + Time * (0.05 + 0.08 * hash(id + 7.0)) + hash(id + 11.0));
    float pulse = smoothstep(0.0, 0.25, run) * smoothstep(1.0, 0.55, run);
    return thread * pulse * step(0.55, hash(id + 13.0)) * (0.3 + 0.7 * hash(id + 17.0));
}

// The great eye: a black disc that swallows the stars, a thin ring of white fire round it and its glow.
vec3 eye(vec3 d, out float hole) {
    float a = acos(clamp(dot(d, Eye), -1.0, 1.0));
    float r = 0.22;
    hole = smoothstep(r + 0.004, r - 0.004, a);
    float rim = exp(-abs(a - r) * 95.0);
    float glow = exp(-max(a - r, 0.0) * 9.0) * 0.55 + exp(-max(a - r, 0.0) * 2.5) * 0.18;
    // The ring is not even: it flickers and is brighter on one side, as if it turned.
    vec3 side = normalize(cross(Eye, vec3(0.0, 1.0, 0.0)) + vec3(1.0e-4));
    float bias = 0.75 + 0.35 * dot(normalize(d - Eye * dot(d, Eye) + vec3(1.0e-5)), side);
    float flick = 0.85 + 0.15 * noise(vec3(atan(d.z, d.x) * 6.0, a * 30.0, Time * 0.6));
    vec3 white = vec3(1.0, 0.98, 1.0);
    vec3 blue = vec3(0.45, 0.75, 1.0);
    return (white * rim * 3.2 * bias * flick + mix(blue, vec3(0.75, 0.6, 1.0), 0.3) * glow * bias) * (1.0 - hole);
}

// Endless space, in every direction.
vec3 cosmos(vec3 d) {
    vec3 c = mix(vec3(0.004, 0.006, 0.02), vec3(0.012, 0.02, 0.06), 0.5 + 0.5 * d.y);
    float neb = fbm(d * 2.6 + vec3(0.0, 0.0, Time * 0.004));
    float wisp = fbm(d * 6.0 + neb * 2.0);
    c += vec3(0.05, 0.08, 0.2) * smoothstep(0.45, 0.85, neb) + vec3(0.12, 0.05, 0.18) * smoothstep(0.6, 0.95, wisp) * 0.6;
    c += stars(d, 180.0, 0.22) + stars(d, 90.0, 0.08) * 1.4 + stars(d, 420.0, 0.35) * 0.5;
    c += vec3(0.6, 0.8, 1.0) * streams(d) * 0.6;
    float hole;
    vec3 e = eye(d, hole);
    return c * (1.0 - hole) + e;
}

// The ground inside: black water, perfectly still but for slow rings out from the caster, giving the void back.
vec3 water(vec3 p, vec3 dir, vec3 n) {
    float r = length(p.xz);
    float ripple = sin(r * 2.2 - Time * 0.9) * exp(-r * 0.02) * 0.015;
    vec3 wn = normalize(n + vec3(p.x, 0.0, p.z) / max(r, 0.5) * ripple);
    vec3 refl = reflect(dir, wn);
    float fres = 0.25 + 0.75 * pow(1.0 - max(dot(-dir, wn), 0.0), 3.0);
    vec3 c = cosmos(refl) * fres * 0.75;
    return c + vec3(0.002, 0.004, 0.01);
}

// Anything standing in the void that is not floor (walls, trees, the sides of hills): black, edged in the eye's light.
vec3 standing(vec3 dir, vec3 n) {
    float rim = pow(1.0 - max(dot(-dir, n), 0.0), 4.0);
    float lit = max(dot(n, Eye), 0.0);
    return vec3(0.01, 0.012, 0.025) + vec3(0.35, 0.5, 0.8) * rim * (0.2 + 0.8 * lit) * 0.6;
}

// ---- the barrier -----------------------------------------------------------------------------------------------

// Where the camera's ray meets the sphere: the near and far distances, or (-1, -1) if it misses.
vec2 sphere(vec3 o, vec3 d) {
    float b = dot(o, d);
    float c = dot(o, o) - Radius * Radius;
    float h = b * b - c;
    if (h < 0.0) {
        return vec2(-1.0);
    }
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

// How high the black has climbed up the sphere from the ring on the ground: nothing above this is closed yet.
float climb() {
    return Radius * Rise * 1.02;
}

// The barrier's skin seen from outside: black as nothing, a sheen sliding over it, deep blue at its edges, and where
// it is still climbing, a seething edge of white light and ink.
vec3 skin(vec3 s, vec3 d, bool outside) {
    vec3 n = normalize(s);
    float fres = pow(1.0 - abs(dot(d, n)), 3.0);
    float sheen = fbm(n * 5.0 + vec3(0.0, Time * 0.04, 0.0));
    vec3 c = vec3(0.004, 0.004, 0.01) + vec3(0.06, 0.1, 0.3) * fres * (outside ? 1.0 : 0.4);
    c += vec3(0.05, 0.06, 0.12) * smoothstep(0.55, 0.8, sheen) * (outside ? 1.0 : 0.3);
    if (!outside) {
        c += stars(n, 120.0, 0.12) * 0.25 * Rise;
    }
    if (Rise < 1.0) {
        float edge = climb() + (fbm(n * 9.0 + vec3(Time * 0.2)) - 0.5) * Radius * 0.08;
        float k = exp(-abs(s.y - edge) * 3.0);
        c += vec3(0.75, 0.9, 1.0) * k * 1.6;
    }
    return c;
}

// ---- the shattering --------------------------------------------------------------------------------------------

// The barrier broken into shards: how near this direction is to a crack, and when its shard falls away (0 to 1).
vec2 shards(vec3 d) {
    vec3 p = d * 11.0;
    vec3 cell = floor(p);
    float best = 8.0;
    float second = 8.0;
    vec3 id = vec3(0.0);
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                vec3 o = cell + vec3(float(x), float(y), float(z));
                vec3 q = o + hash33(o);
                float dist = length(p - q);
                if (dist < best) {
                    second = best;
                    best = dist;
                    id = o;
                } else if (dist < second) {
                    second = dist;
                }
            }
        }
    }
    float edge = second - best;
    float crack = smoothstep(0.03, 0.0, edge) + 0.35 * exp(-edge * 18.0);
    return vec2(crack, hash3(id));
}

void main() {
    vec2 uv = texCoord;
    vec3 c = scene(uv);
    float depth = rawDepth(uv);
    bool sky = depth >= 0.99999;
    vec3 dir = viewDir(uv);
    vec3 rel = relAt(uv);
    float dist = sky ? 1.0e9 : length(rel);
    vec3 p = CamOffset + rel;

    // The ring of ink over the ground, until the world goes.
    if (Ring > 0.0 && !sky && Void < 1.0) {
        float reach = Radius * Ring;
        float r = length(p.xz);
        float ragged = (fbm(vec3(p.xz * 0.35, Time * 0.05)) - 0.5) * 2.2;
        float ground = smoothstep(2.5, 0.5, p.y) * smoothstep(-Radius, -Radius * 0.6, p.y);
        // Over the ground, not up whoever stands on it.
        ground *= smoothstep(0.35, 0.75, normalAt(uv, rel).y);
        float ink = smoothstep(reach + ragged, reach + ragged - 1.2, r) * ground;
        c = mix(c, vec3(0.01, 0.012, 0.03), ink * 0.88);
        if (Ring < 1.0 || Rise < 0.05) {
            float front = exp(-abs(r - reach - ragged * 0.5) * 2.4) * ground;
            c += vec3(0.45, 0.7, 1.0) * front * 0.9;
        }
    }

    // The barrier, from the moment the black starts to climb until it is gone.
    vec3 o = CamOffset;
    bool camIn = Inside > 0.5;
    if (Rise > 0.0 && Void < 1.0) {
        vec2 t = sphere(o, dir);
        float top = climb();
        if (!camIn && t.x > 0.0) {
            vec3 s = o + dir * t.x;
            if (s.y < top && t.x < dist) {
                c = skin(s, dir, true);
            } else {
                // Not yet closed over here: through the gap, the inside of the far wall.
                vec3 s2 = o + dir * t.y;
                if (s2.y < top && t.y < dist) {
                    c = skin(s2, dir, false);
                }
            }
        } else if (camIn && t.y > 0.0) {
            vec3 s = o + dir * t.y;
            if (s.y < top && t.y < dist) {
                c = skin(s, dir, false);
            }
        }
    }

    // Outside, once it has closed: only the black sphere (the void is for those in it).
    if (!camIn && Void >= 1.0) {
        vec2 t = sphere(o, dir);
        if (t.x > 0.0 && t.x < dist) {
            c = skin(o + dir * t.x, dir, true);
        }
    }

    // Inside: the void.
    if (camIn && Void > 0.0) {
        vec3 v;
        if (sky || length(p) > Radius) {
            v = cosmos(dir);
        } else {
            vec3 n = normalAt(uv, rel);
            v = n.y > 0.55 ? water(p, dir, n) : standing(dir, n);
            // The far edge of it fades into space, so nothing ends.
            v = mix(v, cosmos(dir), smoothstep(Radius * 0.55, Radius, length(p)) * 0.8);
        }
        c = mix(c, v, Void);
    }

    // The shattering: cracks of white light all over it, then the shards dropping away, the world behind.
    if (Shatter > 0.0) {
        vec3 basis;
        bool hit = true;
        if (camIn) {
            basis = dir;
        } else {
            vec2 t = sphere(o, dir);
            hit = t.x > 0.0 && t.x < dist;
            basis = normalize(o + dir * max(t.x, 0.0));
        }
        if (hit) {
            vec2 sh = shards(basis);
            float gone = smoothstep(sh.y * 0.6 + 0.15, sh.y * 0.6 + 0.3, Shatter);
            float crack = sh.x * smoothstep(0.0, 0.12, Shatter) * (1.0 - gone);
            c = mix(c, scene(uv), gone);
            c += vec3(0.75, 0.9, 1.0) * crack * 1.6;
        }
        // The flash of it going.
        c += vec3(0.8, 0.9, 1.0) * exp(-Shatter * 14.0) * smoothstep(0.0, 0.02, Shatter) * (camIn ? 0.9 : 0.4);
    }

    fragColor = vec4(c, 1.0);
    gl_FragDepth = depth;
}
