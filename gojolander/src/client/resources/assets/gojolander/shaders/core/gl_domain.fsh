#version 150

// Unlimited Void's full-screen pass over the world. Positions are relative to the middle of the Domain.
//
// Outside it: ink spreading out over the ground in a ragged, branching ring; the black flooding up out of it like a
// liquid, fingers racing ahead of a curling lip, and closing overhead; the sphere it leaves, its skin rippling, stars
// deep inside it, the air bending round its edge and ink pooled where it meets the ground; and at the end, cracks
// running out across it from the side facing you, its shards falling away.
//
// Inside it: the same ink and black, then nothing (black, silence), then the void opening out of the great eye over
// the whole sky: endless space, light flowing past in slow curving rivers towards the eye, the eye a black disc that
// bends the stars round it in a ring of white fire, and still black water underfoot giving all of it back, rippling
// round him. At the end, let go of, it fades away as in the show, dissolving like smoke with the world showing through;
// cut short (its caster fallen), it cracks out from the eye and falls away in shards instead.
//
// Made to be cheap, being drawn over the whole screen every frame: the void's smooth sky comes painted in a panorama
// (Sampler2, gl_sky) and only its sharp parts are worked out here; the surfaces' facing comes from the screen's own
// derivatives; and seen from outside, every pixel nowhere near the sphere or its ink is passed straight through.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform mat4 InvViewProj;
uniform vec2 ScreenSize;
uniform float Time;
// The camera, relative to the middle of the Domain.
uniform vec3 CamOffset;
uniform float Radius;
// How far the ring has spread and the black climbed (0 to 1); how far the world inside has gone to black, and the void
// opened out of the eye (0 to 1); how far the barrier has got through shattering (0 to 1).
uniform float Ring;
uniform float Rise;
uniform float Black;
uniform float Open;
uniform float Shatter;
// How far it has faded away, at its natural end (0 to 1).
uniform float Fade;
// 1 when the camera is inside the sphere.
uniform float Inside;
// The way the great eye lies (a unit vector); where the caster stands, for the ripples; where the cracks start.
uniform vec3 Eye;
uniform vec3 Walker;
uniform vec3 Impact;

in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.14159265;
// The great eye's size in the sky (radians).
const float EYE = 0.21;

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
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

// Three octaves: enough for ink and liquid, at a third of the cost of more.
float fbm(vec3 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        s += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s / 0.875;
}

vec3 scene(vec2 uv) {
    return texture(Sampler0, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

float rawDepth(vec2 uv) {
    return texture(Sampler1, clamp(uv, vec2(0.001), vec2(0.999))).r;
}

// A basis round an axis: two unit vectors square to it and each other.
void basis(vec3 axis, out vec3 t1, out vec3 t2) {
    vec3 up = abs(axis.y) < 0.95 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    t1 = normalize(cross(axis, up));
    t2 = cross(axis, t1);
}

// ---- the void ---------------------------------------------------------------------------------------------------

// Stars on a lattice of directions: in each cell maybe one, a point of some colour and brightness.
vec3 stars(vec3 d, float scale, float density) {
    vec3 p = d * scale;
    vec3 cell = floor(p);
    vec3 h = hash33(cell);
    if (h.x > density) {
        return vec3(0.0);
    }
    float b = smoothstep(0.03 + 0.05 * h.y, 0.0, length(p - (cell + h))) * (0.4 + 1.6 * h.z * h.z);
    float twinkle = 0.75 + 0.25 * sin(Time * (1.0 + 3.0 * h.y) + h.z * 40.0);
    vec3 tint = mix(vec3(0.7, 0.82, 1.0), mix(vec3(1.0, 0.85, 0.7), vec3(0.85, 0.7, 1.0), h.y), h.z);
    return tint * b * twinkle;
}

// Endless space, in direction d: its smooth sky from the panorama; its stars, bent round the eye; and the eye itself,
// a black disc ringed in white fire, a thin band of light across it.
vec3 cosmos(vec3 d) {
    float a = acos(clamp(dot(d, Eye), -1.0, 1.0));
    vec2 pano = vec2(atan(d.z, d.x) / (2.0 * PI) + 0.5, 1.0 - acos(clamp(d.y, -1.0, 1.0)) / PI);
    vec3 c = texture(Sampler2, pano).rgb;
    vec3 side = d - Eye * dot(d, Eye);
    float sl = length(side);
    vec3 t = sl > 1.0e-4 ? side / sl : vec3(0.0, 1.0, 0.0);
    float a2 = a + 0.012 / max(a - EYE * 0.9, 0.01);
    vec3 bent = normalize(Eye * cos(a2) + t * sin(a2));
    c += stars(bent, 180.0, 0.22) + stars(bent, 90.0, 0.08) * 1.4;
    float hole = smoothstep(EYE + 0.003, EYE - 0.003, a);
    vec3 t1;
    vec3 t2;
    basis(Eye, t1, t2);
    float ang = atan(dot(d, t2), dot(d, t1));
    float swirl = 0.7 + 0.5 * noise(vec3(ang * 3.0 - Time * 0.9, a * 25.0, Time * 0.2));
    float rim = exp(-abs(a - EYE) * 110.0) * swirl;
    vec3 bandAxis = normalize(t2 * 0.92 + Eye * 0.38);
    float band = exp(-abs(dot(d, bandAxis)) * 140.0) * smoothstep(EYE * 0.7, EYE * 1.05, a) * smoothstep(EYE * 3.4, EYE * 1.3, a);
    return c * (1.0 - hole) + (vec3(1.0, 0.97, 1.0) * rim * 3.4 + vec3(1.0, 0.93, 0.85) * band * 1.6) * (1.0 - hole);
}

// The ground inside: black water, a mirror, rippling out round him, giving the void back.
vec3 water(vec3 p, vec3 dir, vec3 n) {
    vec2 off = p.xz - Walker.xz;
    float r = length(off);
    float ripple = sin(r * 2.6 - Time * 3.2) * exp(-r * 0.12) * 0.05 + sin(r * 0.9 - Time * 1.1) * exp(-r * 0.04) * 0.012;
    vec3 wn = normalize(n + vec3(off.x, 0.0, off.y) / max(r, 0.4) * ripple);
    vec3 refl = reflect(dir, wn);
    float fres = 0.55 + 0.45 * pow(1.0 - max(dot(-dir, wn), 0.0), 3.0);
    return cosmos(refl) * fres * 0.9 + vec3(0.002, 0.004, 0.01);
}

// Anything standing in the void that is not floor (walls, trees, the sides of hills): black, edged in the eye's light.
vec3 standing(vec3 dir, vec3 n) {
    float rim = pow(1.0 - max(dot(-dir, n), 0.0), 4.0);
    float lit = max(dot(n, Eye), 0.0);
    return vec3(0.01, 0.012, 0.025) + vec3(0.35, 0.5, 0.8) * rim * (0.2 + 0.8 * lit) * 0.6;
}

// What the void shows: its sky, or the world inside it turned to black water and black shapes, fading into space at
// its far edge so nothing ends.
vec3 voidAt(vec3 dir, vec3 p, vec3 n, bool sky) {
    if (sky || length(p) > Radius) {
        return cosmos(dir);
    }
    if (n.y > 0.55) {
        return water(p, dir, n);
    }
    vec3 v = standing(dir, n);
    float far = smoothstep(Radius * 0.6, Radius, length(p));
    return far > 0.0 ? mix(v, cosmos(dir), far * 0.8) : v;
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

// How high the black has climbed up the sphere at this point round it: fingers of it racing up ahead of the rest.
float climb(vec3 s) {
    if (Rise >= 1.0) {
        return Radius * 1.02;
    }
    float az = atan(s.z, s.x);
    float fingers = pow(fbm(vec3(az * 7.0, Time * 0.25, 3.0)), 2.0) * 2.4;
    return Radius * Rise * 1.02 + Radius * 0.16 * fingers * Rise * (1.0 - Rise) * 4.0;
}

// The barrier's skin: black as nothing; from outside a sheen sliding over it, slow ripples, stars deep inside, deep
// blue at its edges; while it climbs, black liquid streaming up, a white-hot lip, drips running back down.
vec3 skin(vec3 s, vec3 d, bool outside) {
    vec3 n = normalize(s);
    float ripple = sin(dot(n, vec3(0.3, 1.0, 0.2)) * 40.0 - Time * 1.6) * 0.5 + noise(n * 6.0 + vec3(0.0, Time * 0.05, 0.0)) - 0.5;
    vec3 rn = normalize(n + 0.04 * vec3(ripple));
    float fres = pow(1.0 - abs(dot(d, rn)), 3.0);
    float sheen = noise(rn * 5.0 + vec3(0.0, Time * 0.04, 0.0));
    vec3 c = vec3(0.004, 0.004, 0.01) + vec3(0.06, 0.1, 0.3) * fres * (outside ? 1.0 : 0.4);
    c += vec3(0.05, 0.06, 0.12) * smoothstep(0.55, 0.85, sheen) * (outside ? 1.0 : 0.3);
    c += stars(refract(d, rn, 0.8), 110.0, 0.12) * (outside ? 0.12 : 0.25 * Rise);
    if (Rise < 1.0) {
        float az = atan(s.z, s.x);
        float top = climb(s);
        // Black liquid streaming up.
        float stream = noise(vec3(az * 26.0, s.y * 0.22 - Time * 2.2, 1.0));
        c += vec3(0.25, 0.35, 0.55) * smoothstep(0.62, 0.8, stream) * (outside ? 0.25 : 0.08);
        // The lip, white-hot, and drips running back down from it.
        float lip = exp(-abs(s.y - top) * 2.2);
        float drip = smoothstep(0.82, 0.95, noise(vec3(az * 60.0, 0.0, 1.0))) * smoothstep(top - Radius * 0.12, top, s.y)
                * step(s.y, top);
        c += vec3(0.75, 0.9, 1.0) * (lip * 1.5 + drip * 0.4);
    }
    return c;
}

// ---- its end ---------------------------------------------------------------------------------------------------

// Fading away, for a direction n (on the sphere, or from the camera): it dissolves like smoke, patch by patch, wisps of
// light along where it is going. x: how much of it is still there; y: the light of its going edge.
vec2 dissolve(vec3 n, float f) {
    float k = fbm(n * 3.2 + vec3(0.0, Time * 0.15, 0.0));
    float edge = f * 1.3 - 0.15;
    float there = smoothstep(edge, edge + 0.14, k);
    float wisp = exp(-abs(k - edge - 0.07) * 38.0) * (1.0 - f) * step(0.001, f);
    return vec2(there, wisp);
}

// The barrier as glass breaking, for a direction n (on the sphere, or from the camera): cracks running out from
// impact in long rays and rings, then the shards falling away from there outward, shrinking and turning as they go,
// their edges catching the light. x: how much of the barrier is still there; y: the light of its cracks and edges.
vec2 shards(vec3 n, vec3 impact, float s) {
    float a = acos(clamp(dot(n, impact), -1.0, 1.0));
    vec3 t1;
    vec3 t2;
    basis(impact, t1, t2);
    float ang = atan(dot(n, t2), dot(n, t1));
    float sectors = 15.0;
    float wander = (noise(vec3(a * 4.0, 7.0, 0.0)) - 0.5) * 0.35;
    float sector = (ang + PI) / (2.0 * PI) * sectors + wander;
    float k = floor(sector);
    float u = fract(sector);
    float rings = pow(a / PI, 0.6) * 9.0 + (noise(vec3(ang * 2.5, k, 1.0)) - 0.5) * 0.5;
    float j = floor(rings);
    float v = fract(rings);
    float h = hash2(vec2(k, j));
    // Its crack lines, in angle, so they are as fine near the start as far out.
    float du = min(u, 1.0 - u) * (2.0 * PI / sectors) * max(sin(a), 0.05);
    float dv = min(v, 1.0 - v) * 0.1;
    float line = min(du, dv);
    float front = clamp(s / 0.3, 0.0, 1.0) * PI * 1.05;
    float cracked = step(a, front);
    float crack = (smoothstep(0.0025, 0.0, line) + exp(-line * 260.0) * 0.25) * cracked;
    // Then the shards go, nearest the start first.
    float starts = 0.3 + 0.45 * (a / PI) + 0.15 * h;
    float f = clamp((s - starts) / 0.25, 0.0, 1.0);
    vec2 local = vec2(u - 0.5, v - 0.5);
    float turn = f * 1.4 * (h - 0.5);
    local = mat2(cos(turn), -sin(turn), sin(turn), cos(turn)) * local;
    float size = 0.5 * (1.0 - f);
    float edgeDist = max(abs(local.x), abs(local.y));
    float there = step(edgeDist, size);
    float rim = f > 0.0 ? smoothstep(size - 0.07, size, edgeDist) * there : 0.0;
    return vec2(there * (1.0 - 0.5 * f), crack * (1.0 - f) + rim * 0.9);
}

void main() {
    vec2 uv = texCoord;
    vec3 c = scene(uv);
    float depth = rawDepth(uv);
    bool sky = depth >= 0.99999;
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 far4 = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec3 dir = normalize(far4.xyz / far4.w);
    vec4 w4 = InvViewProj * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    vec3 rel = sky ? dir * 100000.0 : w4.xyz / w4.w;
    // The surface's facing, from the screen's own derivatives (taken here, where every pixel takes them).
    vec3 fn = normalize(cross(dFdx(rel), dFdy(rel)));
    vec3 n = dot(fn, rel) > 0.0 ? -fn : fn;
    float dist = sky ? 1.0e9 : length(rel);
    vec3 p = CamOffset + rel;
    vec3 o = CamOffset;
    bool camIn = Inside > 0.5;

    // Seen from outside, nothing to do for whatever is nowhere near it: no sphere on its ray, no ink under it.
    if (!camIn) {
        float b = dot(o, dir);
        float closest2 = dot(o, o) - b * b;
        bool nearRay = b < 0.0 && closest2 < Radius * Radius * 1.13;
        bool nearInk = !sky && length(p) < Radius * 1.25 + 8.0;
        if (!nearRay && !nearInk) {
            fragColor = vec4(c, 1.0);
            gl_FragDepth = depth;
            return;
        }
    }

    // The ink over the ground, until the world goes: a ragged ring of it spreading, fingers branching out ahead.
    if (Ring > 0.0 && !sky && !(camIn && Black >= 1.0) && length(p.xz) < Radius * 1.25 + 8.0) {
        float reach = Radius * Ring;
        float r = length(p.xz);
        float az = atan(p.z, p.x);
        float ragged = (fbm(vec3(az * 5.0, Time * 0.05, 0.0)) - 0.5) * 3.0;
        float fingers = pow(fbm(vec3(az * 18.0, 2.0, 0.0)), 3.0) * 6.0 * (1.0 - Ring * 0.6);
        float edge = reach + ragged + fingers;
        float ground = smoothstep(2.5, 0.5, p.y) * smoothstep(-Radius, -Radius * 0.6, p.y);
        // Over the ground, not up whoever stands on it.
        ground *= smoothstep(0.35, 0.75, n.y);
        float ink = smoothstep(edge, edge - 1.5, r) * ground * (1.0 - Shatter) * (1.0 - Fade);
        if (ink > 0.0) {
            float wet = noise(vec3(p.xz * 0.6, Time * 0.1));
            c = mix(c, vec3(0.008, 0.01, 0.025) + vec3(0.03, 0.05, 0.1) * smoothstep(0.6, 0.8, wet), ink * 0.9);
        }
        if (Ring < 1.0) {
            float edgeLight = exp(-abs(r - edge) * 3.0) * ground;
            c += vec3(0.35, 0.55, 0.9) * edgeLight * 0.35;
        }
    }

    // Ink pooled round its foot, once it stands, seen from outside.
    if (!camIn && Rise > 0.0 && !sky) {
        float beyond = length(p) - Radius;
        float pool = smoothstep(3.0, 0.0, beyond) * step(0.0, beyond) * smoothstep(2.0, 0.0, p.y + 0.5) * Rise;
        c = mix(c, vec3(0.006, 0.008, 0.02), pool * 0.7 * (1.0 - Shatter) * (1.0 - Fade));
    }

    // The barrier, from the moment the black starts to climb until it is gone. Outside, it hides what is inside it.
    if (Rise > 0.0 && !camIn) {
        vec2 t = sphere(o, dir);
        if (t.x > 0.0) {
            vec3 s = o + dir * t.x;
            if (s.y < climb(s) && t.x < dist) {
                vec3 k = skin(s, dir, true);
                if (Shatter > 0.0) {
                    vec2 sh = shards(normalize(s), Impact, Shatter);
                    k = mix(c, k, sh.x) + vec3(0.8, 0.92, 1.0) * sh.y * 0.9;
                }
                if (Fade > 0.0) {
                    vec2 fd = dissolve(normalize(s), Fade);
                    k = mix(c, k, fd.x) + vec3(0.6, 0.8, 1.0) * fd.y * 0.8;
                }
                c = k;
            } else {
                // Not yet closed over here: through the gap, the inside of the far wall.
                vec3 s2 = o + dir * t.y;
                if (s2.y < climb(s2) && t.y < dist) {
                    c = skin(s2, dir, false);
                }
            }
        } else if (Rise >= 1.0 && Shatter < 1.0) {
            // The air bending round its edge.
            float b = dot(o, dir);
            float closest = sqrt(max(dot(o, o) - b * b, 0.0));
            float haze = smoothstep(Radius * 1.06, Radius, closest) * step(b, 0.0) * (1.0 - Shatter) * (1.0 - Fade);
            if (haze > 0.0) {
                vec2 wobble = vec2(sin(Time * 2.3 + uv.y * 90.0), cos(Time * 1.9 + uv.x * 70.0)) * 0.0025 * haze;
                c = mix(c, scene(uv + wobble), haze) + vec3(0.02, 0.04, 0.1) * haze * 0.5;
            }
        }
    }

    // Inside: the black climbing round and over, then nothing, then the void opening out of the eye.
    if (camIn) {
        if (Rise > 0.0 && Black < 1.0) {
            vec2 t = sphere(o, dir);
            vec3 s = o + dir * t.y;
            if (t.y > 0.0 && s.y < climb(s) && t.y < dist) {
                c = skin(s, dir, false);
            }
        }
        if (Black > 0.0) {
            vec3 world = scene(uv);
            c = mix(c, vec3(0.0), Black);
            if (Open > 0.0) {
                float a = acos(clamp(dot(dir, Eye), -1.0, 1.0));
                float reach = Open * PI * 1.08;
                float shown = smoothstep(reach, reach - 0.18, a);
                if (shown > 0.0) {
                    c = mix(c, voidAt(dir, p, n, sky), shown);
                }
                // The edge of it opening, burning.
                c += vec3(0.7, 0.85, 1.0) * exp(-abs(a - reach) * 22.0) * (1.0 - Open) * 1.8;
            }
            if (Shatter > 0.0) {
                vec2 sh = shards(dir, Impact, Shatter);
                c = mix(world, c, sh.x) + vec3(0.8, 0.92, 1.0) * sh.y * 1.0;
                // The flash of it going.
                c += vec3(0.8, 0.9, 1.0) * exp(-abs(Shatter - 0.3) * 18.0) * 0.35;
            }
            if (Fade > 0.0) {
                vec2 fd = dissolve(dir, Fade);
                c = mix(world, c, fd.x) + vec3(0.6, 0.8, 1.0) * fd.y * 0.9;
            }
        }
    }

    fragColor = vec4(c, 1.0);
    gl_FragDepth = depth;
}
