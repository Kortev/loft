#version 150

// Ω-00 Ginnungagap's full-screen pass: the bad signal when the key turns, the impact frames, the erasure
// that turns everything black one block at a time, and the black that is left afterwards.
// Style 1 white (black ground, the mirror universe drawn as white with ink lines), 2 black with white lines,
// 3 red sky with a black world and a white mirror, 4 negative. Extras: 1 speed lines, 2 cracks,
// 4 a double image, 8 a slab of ink across the frame.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 InvViewProj;
uniform vec2 ScreenSize;
uniform float Time;
uniform vec3 CamOffset;
uniform float Front;
uniform float Clamp;
uniform int Style;
uniform int Extras;
uniform vec3 Panels;
uniform int PanelOn;
uniform vec2 Focus;
uniform float Punch;
uniform float Seed;
uniform float Glitch;
uniform float Flash;
uniform float Black;
uniform vec3 ShatterFrom;
uniform float Shatter;
uniform float CosmosTime;
uniform vec4 Lock;
uniform float CloudY;

in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.14159265;

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

float hash(float n) {
    return fract(sin(n * 12.9898 + 4.1414) * 43758.5453);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float hash3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

vec3 scene(vec2 uv) {
    return texture(Sampler0, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

float rawDepth(vec2 uv) {
    return texture(Sampler1, clamp(uv, vec2(0.001), vec2(0.999))).r;
}

float logDepth(vec2 uv) {
    return log(1.00002 - rawDepth(uv));
}

bool isSky(vec2 uv) {
    return rawDepth(uv) >= 0.99999;
}

// Clouds are part of our sky: they break and fall away with it.
bool isCloud(vec2 uv) {
    float d = rawDepth(uv);
    if (d >= 0.99999 || CloudY > 1000.0) {
        return false;
    }
    vec4 w = InvViewProj * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec3 rel = w.xyz / w.w;
    return rel.y > CloudY - 1.0 && rel.y < CloudY + 5.0 && dot(rel.xz, rel.xz) > 400.0;
}

bool skyLike(vec2 uv) {
    return isSky(uv) || isCloud(uv);
}

// The other universe: anything cold and saturated, blue through magenta. Ours runs warm.
bool isOther(vec3 c) {
    float hi = max(c.r, max(c.g, c.b));
    float lo = min(c.r, min(c.g, c.b));
    return hi - lo > 0.2 && c.b > c.g && c.b > c.r * 0.8;
}

float edgeAt(vec2 uv) {
    vec2 px = 1.5 / ScreenSize;
    float d = logDepth(uv);
    float e = 0.0;
    e = max(e, abs(d - logDepth(uv + vec2(px.x, 0.0))));
    e = max(e, abs(d - logDepth(uv - vec2(px.x, 0.0))));
    e = max(e, abs(d - logDepth(uv + vec2(0.0, px.y))));
    e = max(e, abs(d - logDepth(uv - vec2(0.0, px.y))));
    float l = dot(scene(uv), vec3(0.299, 0.587, 0.114));
    float lx = dot(scene(uv + vec2(px.x, 0.0)), vec3(0.299, 0.587, 0.114));
    float ly = dot(scene(uv + vec2(0.0, px.y)), vec3(0.299, 0.587, 0.114));
    float ce = max(abs(l - lx), abs(l - ly));
    return max(smoothstep(0.18, 0.4, e), smoothstep(0.16, 0.3, ce));
}

vec3 inkColor(int style) {
    return style == 2 ? vec3(1.0) : style == 4 ? vec3(1.0) : vec3(0.0);
}

// One impact-frame look applied to the scene at uv.
vec3 ink(int style, vec2 uv) {
    vec3 c = scene(uv);
    bool sky = skyLike(uv);
    if (style == 4) {
        // A negative, but only in black and white: anything else turns the other universe green.
        float lum = sky ? 0.9 : dot(c, vec3(0.299, 0.587, 0.114));
        return vec3(1.0 - smoothstep(0.12, 0.5, lum));
    }
    bool other = !sky && isOther(c);
    float edge = edgeAt(uv);
    if (style == 1) {
        vec3 base = sky ? vec3(1.0) : other ? vec3(1.0) : vec3(0.0);
        return mix(base, vec3(0.0), edge);
    }
    if (style == 2) {
        float lum = dot(c, vec3(0.299, 0.587, 0.114));
        // Black on black: only the edges and the brightest parts of the mirror universe show, in white.
        return mix(vec3(0.0), vec3(1.0), max(edge, other ? smoothstep(0.55, 0.8, lum) * 0.6 : 0.0));
    }
    // 3: red
    vec3 base = sky ? vec3(0.85, 0.06, 0.11) : other ? vec3(1.0) : vec3(0.0);
    return mix(base, vec3(0.0), edge);
}

vec2 punch(vec2 uv, float zoom) {
    return Focus + (uv - Focus) / zoom;
}

float speedLines(vec2 uv) {
    vec2 p = (uv - Focus) * vec2(ScreenSize.x / ScreenSize.y, 1.0);
    float r = length(p);
    float a = atan(p.y, p.x) / PI * 70.0 + Seed * 13.0;
    float lane = floor(a);
    float on = step(0.68, hash(lane));
    float start = 0.18 + 0.5 * hash(lane + 31.0);
    float width = 0.25 + 0.6 * hash(lane + 17.0);
    float across = abs(fract(a) - 0.5) * 2.0;
    return on * step(start, r) * step(across, width * smoothstep(start, start + 0.6, r));
}

// Cracks running out from the point of contact: a dozen jagged lines that wander as they go, with a few branches.
float cracks(vec2 uv) {
    vec2 p = (uv - Focus) * vec2(ScreenSize.x / ScreenSize.y, 1.0);
    float r = length(p);
    if (r > 1.1 || r < 0.01) {
        return 0.0;
    }
    float a = atan(p.y, p.x);
    float line = 0.0;
    for (int i = 0; i < 11; i++) {
        float fi = float(i);
        float base = (fi + 0.6 * hash(fi + Seed)) / 11.0 * 2.0 * PI;
        // Short straight runs with sharp kinks between them, like a crack in glass.
        float steps = r * 16.0;
        float seg = floor(steps);
        float drift = mix(hash(seg + fi * 13.0 + Seed) - 0.5, hash(seg + 1.0 + fi * 13.0 + Seed) - 0.5, fract(steps)) * 0.16 / max(r, 0.08);
        float da = abs(mod(a - base - drift + PI, 2.0 * PI) - PI);
        float width = (0.0012 + 0.0028 * (1.0 - min(r, 1.0))) / r;
        float reach = 0.3 + 0.75 * hash(fi * 7.0 + Seed);
        line = max(line, step(da, width) * step(r, reach));
        // A branch splitting off partway out.
        float from = 0.12 + 0.3 * hash(fi * 3.0 + Seed);
        float turn = (hash(fi * 5.0 + Seed) - 0.5) * 0.9;
        float bd = abs(mod(a - base - drift - turn * clamp((r - from) * 3.0, 0.0, 1.0) + PI, 2.0 * PI) - PI);
        line = max(line, step(bd, width * 0.7) * step(from, r) * step(r, from + 0.25));
    }
    return line;
}

float slab(vec2 uv) {
    float v = dot(uv - vec2(0.5), normalize(vec2(0.42, 1.0)));
    float at = (hash(Seed + 2.0) - 0.5) * 0.4;
    float w = 0.06 + 0.05 * hash(Seed + 5.0);
    return step(at, v) * step(v, at + w);
}

vec3 frame(int style, vec2 uv) {
    vec2 suv = punch(uv, 1.0 + Punch);
    vec3 c = ink(style, suv);
    if ((Extras & 4) != 0) {
        c = mix(c, ink(style, suv + vec2(0.022, -0.014)), 0.38);
    }
    vec3 inkC = inkColor(style);
    if ((Extras & 1) != 0) {
        c = mix(c, inkC, speedLines(uv));
    }
    if ((Extras & 2) != 0) {
        c = mix(c, inkC, cracks(uv));
    }
    if ((Extras & 8) != 0) {
        c = mix(c, inkC, slab(uv));
    }
    return c;
}

// Three slanted comic panels, each its own look and its own zoom on the point of contact.
vec3 panels(vec2 uv) {
    float slant = (1.0 - uv.y) * 0.05;
    float x = uv.x - slant;
    float g1 = 0.33;
    float g2 = 0.66;
    float gutter = 0.008;
    if (abs(x - g1) < gutter || abs(x - g2) < gutter) {
        return vec3(1.0);
    }
    if (abs(x - g1) < gutter + 0.003 || abs(x - g2) < gutter + 0.003) {
        return vec3(0.0);
    }
    int index = x < g1 ? 0 : x < g2 ? 1 : 2;
    float centre = index == 0 ? g1 * 0.5 : index == 1 ? (g1 + g2) * 0.5 : (g2 + 1.0) * 0.5;
    float zoom = index == 0 ? 2.2 : index == 1 ? 1.0 : 1.5;
    vec2 local = vec2(Focus.x + (uv.x - centre - slant) / zoom, Focus.y + (uv.y - 0.5) / zoom);
    int style = int(index == 0 ? Panels.x : index == 1 ? Panels.y : Panels.z);
    vec3 c = ink(style, local);
    return mix(c, inkColor(style), speedLines(local) * float(index == 1));
}

vec3 viewDir(vec2 uv) {
    vec4 far = InvViewProj * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    return normalize(far.xyz / far.w);
}

// The sky breaks like glass from straight over the target: long shards running out from the break, each one
// cracking, then dropping away into the other universe behind it. Our ground takes on that universe's light.
vec2 shardSeed(vec2 g, float n) {
    vec2 id = vec2(g.x, mod(g.y, n));
    return g + 0.12 + 0.76 * vec2(c_hash(vec3(id, 1.3)), c_hash(vec3(id, 7.9)));
}

vec3 shatter(vec2 uv, vec3 c) {
    if (Shatter <= 0.0) {
        return c;
    }
    if (!skyLike(uv)) {
        float k = smoothstep(0.0, 2.4, Shatter) * 0.5;
        float lum = dot(c, vec3(0.299, 0.587, 0.114));
        vec3 lit = mix(vec3(lum), c, 0.75) * vec3(0.8, 0.68, 1.22);
        return mix(c, lit, k);
    }
    vec3 d = viewDir(uv);
    vec3 s = normalize(ShatterFrom);
    vec3 e1 = normalize(cross(s, abs(s.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 e2 = cross(s, e1);
    float theta = acos(clamp(dot(d, s), -1.0, 1.0));
    float phi = atan(dot(d, e2), dot(d, e1));
    // Log-polar, so the shards grow with distance from the break but keep their long, thin shape.
    const float A = 2.1;
    const float N = 22.0;
    vec2 p = vec2(log(theta + 0.02) * A, (phi / 6.2831853 + 0.5) * N);
    p += (vec2(c_noise(d * 23.0), c_noise(d * 23.0 + 7.3)) - 0.5) * 0.28;
    vec2 cell = floor(p);
    float best = 9.0;
    vec2 seed = vec2(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = shardSeed(cell + vec2(float(x), float(y)), N);
            float dist = length(p - o);
            if (dist < best) {
                best = dist;
                seed = o;
            }
        }
    }
    vec2 own = floor(seed);
    vec2 id = vec2(own.x, mod(own.y, N));
    // How far this point is from the nearest edge of its shard.
    float edge = 9.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = shardSeed(own + vec2(float(x), float(y)), N);
            vec2 to = o - seed;
            if (dot(to, to) > 1.0e-5) {
                edge = min(edge, dot(0.5 * (seed + o) - p, normalize(to)));
            }
        }
    }
    // One pixel, measured in shard space, so the cracks stay hairline however big the shards get.
    float ang = length(viewDir(uv + vec2(1.0 / ScreenSize.x, 0.0)) - d);
    float px = ang * max(A / (theta + 0.02), N / (6.2831853 * max(sin(theta), 0.05)));

    float from = exp(seed.x / A) - 0.02;
    float local = Shatter - from - c_hash(vec3(id, 3.7)) * 0.22;
    float crack = smoothstep(-0.32, -0.04, local);
    float fall = smoothstep(0.0, 0.3, local);
    vec3 behind = cosmos(d, CosmosTime);
    // As it falls the shard shrinks back from its edges, darkening, with its broken rim burning white.
    float inset = fall * 0.55;
    if (fall >= 1.0) {
        return behind;
    }
    if (edge < inset) {
        return behind + vec3(0.6, 0.85, 1.0) * exp(-(inset - edge) / (8.0 * px)) * 0.5 * (1.0 - fall);
    }
    float tilt = 0.8 + 0.4 * c_hash(vec3(id, 5.1));
    vec3 piece = c * mix(1.0, tilt, crack) * (1.0 - 0.6 * fall);
    float rim = 1.0 - smoothstep(0.0, px * (1.3 + 2.5 * fall), edge - inset);
    return piece + vec3(0.85, 1.0, 1.0) * rim * crack * 1.4;
}

// The lock the key goes into, in the empty air: a slit of light that opens where its tip goes in, then cracks
// that run out from it across the picture as it turns.
vec3 lockLight(vec2 uv, vec3 c) {
    if (Lock.z <= 0.0 && Lock.w <= 0.0) {
        return c;
    }
    vec2 p = (uv - Lock.xy) * vec2(ScreenSize.x / ScreenSize.y, 1.0);
    float h = 0.2 * Lock.z;
    float w = 0.0018 + 0.006 * Lock.w;
    float slit = (1.0 - smoothstep(w * 0.5, w, abs(p.x))) * (1.0 - smoothstep(h * 0.55, h, abs(p.y))) * Lock.z;
    float glow = exp(-abs(p.x) / (0.012 + 0.05 * Lock.w)) * (1.0 - smoothstep(0.0, h * 1.5 + 0.001, abs(p.y))) * Lock.z;
    float crack = 0.0;
    if (Lock.w > 0.0) {
        float a = atan(p.y, p.x);
        float r = length(p);
        const float n = 11.0;
        for (int k = -1; k <= 1; k++) {
            float sector = floor((a / 6.2831853 + 0.5) * n) + float(k);
            float ca = (sector + 0.5 + (hash(sector + 3.0) - 0.5) * 0.7) / n * 6.2831853 - 3.14159265;
            float jag = (c_noise(vec3(r * 16.0, sector * 7.1, 0.5)) - 0.5) * 0.22 + (c_noise(vec3(r * 60.0, sector * 3.3, 2.5)) - 0.5) * 0.05;
            float d = abs(sin(a - ca - jag)) * r;
            float reach = Lock.w * 1.5 * (0.45 + 0.55 * hash(sector + 9.0));
            float px = 1.0 / ScreenSize.y;
            crack = max(crack, (1.0 - smoothstep(px * 0.6, px * 1.8, d)) * (1.0 - smoothstep(reach * 0.85, reach, r)) * step(0.015, r));
        }
    }
    return c + vec3(0.75, 0.95, 1.0) * (slit * 2.5 + glow * 0.7) + vec3(0.85, 1.0, 1.0) * crack * 1.6;
}

vec3 erase(vec2 uv, vec3 c) {
    if (Front < 0.0) {
        return c;
    }
    float d = rawDepth(uv);
    vec2 ndc = uv * 2.0 - 1.0;
    vec3 rel;
    if (d >= 0.99999) {
        vec4 far = InvViewProj * vec4(ndc, 1.0, 1.0);
        rel = normalize(far.xyz / far.w) * 600.0;
    } else {
        vec4 w = InvViewProj * vec4(ndc, d * 2.0 - 1.0, 1.0);
        rel = w.xyz / w.w;
    }
    // Cells grow with distance so the far ones still read as blocks on screen.
    float size = exp2(floor(log2(max(1.0, length(rel) / 40.0))));
    vec3 cell = floor((rel + CamOffset) / size) * size;
    vec3 mid = cell + 0.5 * size;
    if (Clamp > 0.0 && length(mid.xz) > Clamp) {
        return c;
    }
    float m = abs(mid.x) + abs(mid.y) + abs(mid.z) + hash3(cell) * 7.0 * size;
    if (m < Front - 1.6 * size) {
        return vec3(0.0);
    }
    if (m < Front) {
        return mix(c, vec3(1.0), 0.92);
    }
    return c;
}

void main() {
    vec2 uv = texCoord;
    vec3 c;
    if (PanelOn != 0) {
        c = panels(uv);
    } else if (Style != 0) {
        c = frame(Style, uv);
    } else {
        vec2 guv = uv;
        if (Glitch > 0.0) {
            float band = floor(uv.y * 38.0 + floor(Seed) * 7.0);
            float h = hash(band);
            if (h > 1.0 - 0.55 * Glitch) {
                guv.x += (hash(band + 1.7) - 0.5) * 0.16 * Glitch;
            }
            float split = 0.006 * Glitch;
            c = vec3(scene(guv + vec2(split, 0.0)).r, scene(guv).g, scene(guv - vec2(split, 0.0)).b);
            float scan = step(0.985 - 0.05 * Glitch, hash(floor(uv.y * ScreenSize.y * 0.5) + floor(Seed) * 3.1));
            vec3 tint = hash(floor(uv.y * ScreenSize.y * 0.5) + 9.0) > 0.5 ? vec3(0.0, 1.0, 1.0) : vec3(1.0, 0.0, 1.0);
            c = mix(c, tint, scan * 0.6 * Glitch);
        } else {
            c = scene(uv);
        }
        c = lockLight(uv, c);
        c = shatter(uv, c);
        c = erase(uv, c);
    }
    c = mix(c, vec3(1.0), clamp(Flash, 0.0, 1.0));
    c = mix(c, vec3(0.0), clamp(Black, 0.0, 1.0));
    fragColor = vec4(c, 1.0);
}
