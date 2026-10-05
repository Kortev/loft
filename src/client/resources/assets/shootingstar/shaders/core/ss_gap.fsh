#version 150

// Ω-00 Ginnungagap's full-screen pass: the bad signal when the key turns, the light of the burst on the world
// with its square shock front running out over the ground and the other universe's sky spilling over ours, the
// impact frames, the erasure that turns everything black one block at a time, and the black that is left.
// Style 1 white (black ground, the other universe drawn white with ink lines), 2 black with white lines,
// 3 violet sky with a black world and a white universe, 4 negative. Extras: 1 speed lines, 2 cracks,
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
// The burst: its centre relative to the target block's corner and its half size (0 for none), the colour and
// strength of its light, the square front running out from it over the ground (below 0 for none), and how much of
// the sky has become the other universe's.
uniform vec4 Burst;
uniform vec4 Odin;
uniform vec4 OdinState;
uniform vec3 OdinFacing;
uniform vec3 BurstLight;
uniform float Shock;
uniform float SkyMix;

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
    bool sky = isSky(uv);
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
    // 3: violet
    vec3 base = sky ? vec3(0.42, 0.12, 0.85) : other ? vec3(1.0) : vec3(0.0);
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
// Position of the pixel's surface relative to the camera; the sky is taken as 600 blocks out.
vec3 relAt(vec2 uv) {
    float d = rawDepth(uv);
    vec2 ndc = uv * 2.0 - 1.0;
    if (d >= 0.99999) {
        vec4 far = InvViewProj * vec4(ndc, 1.0, 1.0);
        return normalize(far.xyz / far.w) * 600.0;
    }
    vec4 w = InvViewProj * vec4(ndc, d * 2.0 - 1.0, 1.0);
    return w.xyz / w.w;
}

// The burst lights the world round it in its own colours, and its front runs out over the ground as a square.
vec3 burst(vec2 uv, vec3 c) {
    if (isSky(uv)) {
        c = SkyMix > 0.0 ? mix(c, cosmos(viewDir(uv), Time), SkyMix) : c;
        // The sky goes dark as the black spreads under it.
        return Front >= 0.0 ? c * (1.0 - smoothstep(40.0, 360.0, Front)) : c;
    }
    if (Burst.w <= 0.0 && Shock < 0.0) {
        return c;
    }
    vec3 p = relAt(uv);
    vec3 d = p + CamOffset - Burst.xyz;
    if (Burst.w > 0.0) {
        float h = Burst.w;
        float fall = h * h / (dot(d, d) * 0.35 + h * h);
        // Which way the surface faces, from the depth of the pixels beside it: faces turned to the light take it, the
        // far sides of hills and trees stay in their own shadow.
        vec2 px = 1.0 / ScreenSize;
        vec3 n = cross(relAt(uv + vec2(px.x, 0.0)) - p, relAt(uv + vec2(0.0, px.y)) - p);
        float facing = 0.5;
        if (dot(n, n) > 1.0e-10) {
            n = normalize(n);
            n = dot(n, p) > 0.0 ? -n : n;
            facing = max(dot(n, normalize(-d)), 0.0);
        }
        c += c * BurstLight * fall * (0.4 + 3.2 * facing) + BurstLight * fall * 0.12;
    }
    if (Shock >= 0.0) {
        float cheb = max(abs(d.x), abs(d.z));
        float width = 2.5 + Shock * 0.035;
        float band = exp(-pow((cheb - Shock) / width, 2.0)) * (1.0 - smoothstep(14.0, 40.0, abs(d.y)));
        float behind = (1.0 - smoothstep(Shock - width, Shock, cheb)) * (1.0 - smoothstep(14.0, 40.0, abs(d.y)));
        c = mix(c, c * vec3(0.55, 0.45, 0.85), behind * 0.6);
        c += vec3(0.85, 0.7, 1.0) * band * 1.4;
    }
    return c;
}

vec3 erase(vec2 uv, vec3 c) {
    if (Front < 0.0) {
        return c;
    }
    vec3 rel = relAt(uv);
    // Cells grow with distance so the far ones still read as blocks on screen.
    float size = exp2(floor(log2(max(1.0, length(rel) / 40.0))));
    vec3 cell = floor((rel + CamOffset) / size) * size;
    vec3 mid = cell + 0.5 * size;
    if (Clamp > 0.0 && length(mid.xz) > Clamp) {
        return c;
    }
    // Up and down count for half, so the valleys go with the ground round them rather than long after.
    float m = abs(mid.x) + 0.5 * abs(mid.y) + abs(mid.z) + hash3(cell) * 7.0 * size;
    if (m < Front - 1.6 * size) {
        return vec3(0.0);
    }
    if (m < Front) {
        // The front's white edge, gone by the time the black has the sky too.
        return mix(c, vec3(1.0), 0.92 * (1.0 - smoothstep(220.0, 420.0, Front)));
    }
    return c;
}

// Odin in the void, for the rebuild: a colossal cloaked figure in a wide-brimmed hat, his cloak full of stars, one eye
// burning, Gungnir upright in his left hand and his right arm coming up and sweeping down. Raymarched in his own
// frame (feet at the origin, one unit his height, facing the shooter along +z). Odin is the feet (relative to the
// target) and height in blocks; OdinState is how much he is there, his eye, his arm (0 down, 1 raised), the light in
// his hand; OdinFacing the way from the shooter to him.

float sdSeg(vec3 p, vec3 a, vec3 b, float ra, float rb) {
    vec3 pa = p - a;
    vec3 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - mix(ra, rb, h);
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

vec3 odinHand(float arm) {
    return mix(vec3(0.25, 0.42, 0.13), vec3(0.42, 1.02, 0.24), arm);
}

float odinShape(vec3 p, float arm) {
    // The cloak, falling heavy and wide from his shoulders to the ground, folds in it.
    float folds = 0.012 * sin(atan(p.x, p.z) * 9.0) * smoothstep(0.75, 0.1, p.y);
    float cloak = sdSeg(p, vec3(0.0, 0.0, -0.02), vec3(0.0, 0.76, 0.0), 0.42, 0.15) + folds;
    float body = smin(cloak, sdSeg(p, vec3(-0.19, 0.73, 0.0), vec3(0.19, 0.73, 0.0), 0.085, 0.085), 0.08);
    // Head, beard, and the wide-brimmed hat.
    body = smin(body, length(p - vec3(0.0, 0.855, 0.02)) - 0.075, 0.03);
    body = smin(body, sdSeg(p, vec3(0.0, 0.83, 0.07), vec3(0.0, 0.66, 0.1), 0.055, 0.012), 0.02);
    float brim = max(abs(p.y - 0.915) - 0.006, length(p.xz - vec2(0.0, 0.01)) - 0.2);
    float crown = sdSeg(p, vec3(0.0, 0.91, 0.01), vec3(0.0, 1.03, 0.0), 0.085, 0.035);
    body = min(body, min(brim, crown));
    // His left arm down to the spear; his right arm coming up and sweeping down.
    body = smin(body, sdSeg(p, vec3(-0.2, 0.7, 0.02), vec3(-0.27, 0.5, 0.1), 0.06, 0.035), 0.06);
    body = smin(body, sdSeg(p, vec3(0.2, 0.7, 0.02), odinHand(arm), 0.06, 0.03), 0.06);
    // Gungnir.
    float spear = min(sdSeg(p, vec3(-0.28, -0.02, 0.11), vec3(-0.28, 1.25, 0.11), 0.006, 0.006),
                      sdSeg(p, vec3(-0.28, 1.25, 0.11), vec3(-0.28, 1.34, 0.11), 0.018, 0.0));
    return min(body, spear);
}

float odinHash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

// Glow round the point q seen along the ray (ro, rd), q and the ray in blocks.
float glowAt(vec3 ro, vec3 rd, vec3 q, float size) {
    float along = max(dot(q - ro, rd), 0.0);
    float miss = length(ro + rd * along - q);
    return exp(-pow(miss / size, 2.0)) + 0.15 * exp(-miss / (size * 6.0));
}

// c is what is there already; sceneDist how far its surface is (blocks), or < 0 where nothing stands in front of him.
vec3 odin(vec3 ro, vec3 rd, vec3 c, float sceneDist) {
    float there = OdinState.x;
    if (there <= 0.001) {
        return c;
    }
    float h = Odin.w;
    vec3 feet = Odin.xyz;
    vec3 fwd = -normalize(vec3(OdinFacing.x, 0.0, OdinFacing.z));
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), fwd));
    // Into his frame, in units of his height.
    vec3 o = ro - feet;
    vec3 lo = vec3(dot(o, right), o.y, dot(o, fwd)) / h;
    vec3 ld = vec3(dot(rd, right), rd.y, dot(rd, fwd));
    vec3 centre = vec3(0.0, 0.6, 0.0);
    // Behind him, a cold gold halo, so he stands out against the black.
    vec3 head = feet + vec3(0.0, 0.88 * h, 0.0) - fwd * 0.3 * h;
    vec3 toHead = normalize(head - ro);
    float ang = acos(clamp(dot(rd, toHead), -1.0, 1.0));
    vec3 col = c;
    bool front = sceneDist < 0.0;
    vec3 halo = (vec3(1.0, 0.72, 0.38) * exp(-ang / 0.12) * 0.7 + vec3(0.55, 0.4, 1.0) * exp(-ang / 0.4) * 0.22) * there;
    // March.
    vec3 oc = lo - centre;
    float b = dot(oc, ld);
    float disc = b * b - (dot(oc, oc) - 0.75 * 0.75);
    float hit = -1.0;
    if (disc > 0.0) {
        float t = max(-b - sqrt(disc), 0.0);
        float t1 = -b + sqrt(disc);
        for (int i = 0; i < 90; i++) {
            float d = odinShape(lo + ld * t, OdinState.z);
            if (d < 0.0008 * (1.0 + t)) {
                hit = t;
                break;
            }
            t += d * 0.9;
            if (t > t1) {
                break;
            }
        }
    }
    float tBlocks = hit * h;
    bool shown = hit >= 0.0 && (front || tBlocks < sceneDist);
    if (shown) {
        vec3 p = lo + ld * hit;
        vec2 e = vec2(0.0015, -0.0015);
        vec3 n = normalize(e.xyy * odinShape(p + e.xyy, OdinState.z) + e.yyx * odinShape(p + e.yyx, OdinState.z)
                         + e.yxy * odinShape(p + e.yxy, OdinState.z) + e.xxx * odinShape(p + e.xxx, OdinState.z));
        // A shape cut out of the light behind him: black, edged thinly in gold where his outline turns away.
        float rim = pow(1.0 - max(dot(n, -ld), 0.0), 5.0);
        vec3 shade = vec3(0.006, 0.005, 0.012) + vec3(1.0, 0.72, 0.38) * rim * 0.9 + vec3(0.5, 0.42, 1.0) * rim * 0.25;
        // His cloak is the night: stars in it.
        float star = step(0.996, odinHash(floor(p * 420.0)));
        shade += vec3(0.85, 0.9, 1.0) * star * 1.2 * smoothstep(0.85, 0.6, p.y);
        col = mix(c, shade, there);
    } else if (front || hit < 0.0) {
        col = c + halo;
    }
    // His one eye, the spear's point and the light in his hand.
    vec3 eye = feet + right * 0.03 * h + vec3(0.0, 0.868 * h, 0.0) + fwd * 0.09 * h;
    col += vec3(1.0, 0.86, 0.5) * glowAt(ro, rd, eye, 0.006 * h) * 6.0 * OdinState.y * there;
    vec3 tip = feet - right * 0.28 * h + vec3(0.0, 1.34 * h, 0.0) + fwd * 0.11 * h;
    col += vec3(0.9, 0.8, 1.0) * glowAt(ro, rd, tip, 0.008 * h) * 2.5 * there;
    vec3 hl = odinHand(OdinState.z);
    vec3 hand = feet + right * hl.x * h + vec3(0.0, hl.y * h, 0.0) + fwd * hl.z * h;
    col += vec3(0.95, 0.9, 1.0) * glowAt(ro, rd, hand, 0.02 * h) * 4.0 * OdinState.w * there;
    return col;
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
        c = burst(uv, c);
        c = erase(uv, c);
        if (OdinState.x > 0.0) {
            // Nothing stands in front of him in the sky or where the black still is; elsewhere the world remade does.
            bool open = isSky(uv) || dot(c, c) < 1.0e-6;
            c = odin(CamOffset, viewDir(uv), c, open ? -1.0 : length(relAt(uv)));
        }
    }
    c = mix(c, vec3(1.0), clamp(Flash, 0.0, 1.0));
    c = mix(c, vec3(0.0), clamp(Black, 0.0, 1.0));
    fragColor = vec4(c, 1.0);
    gl_FragDepth = rawDepth(uv);
}
