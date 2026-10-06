#version 150

// Ω-00 Ginnungagap's full-screen pass: the bad signal when the key turns, the light of the burst on the world
// with its square shock front running out over the ground and the other universe's sky spilling over ours, the
// impact frames, the erasure that turns everything black one block at a time, and the black that is left.
// Style 1 white (black ground, the other universe drawn white with ink lines), 2 black with white lines,
// 3 violet sky with a black world and a white universe, 4 negative. Extras: 1 speed lines, 2 cracks,
// 4 a double image, 8 a slab of ink across the frame.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
// Yggdrasil, drawn into a picture of its own (TreeRender); Odin is its foot (relative to the target) and height, OdinState.x how much it is there.
uniform sampler2D Sampler2;
uniform mat4 InvViewProj;
uniform vec2 ScreenSize;
uniform float Time;
uniform vec3 CamOffset;
uniform float Front;
// 1 while the world is being rebuilt: then the front runs outward, the world remade inside it and the void beyond.
uniform float Remake;
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
        // The sky goes dark as the black spreads under it, and comes back as the rebuilt world spreads out again.
        if (Remake > 0.0) {
            return c * smoothstep(60.0, 520.0, Front);
        }
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
    if (Remake > 0.0) {
        // Built back block by block out from the middle: beyond the front, nothing; at it, each block coming in as a
        // white-hot cube outlined in light, cooling to itself over the next few blocks behind.
        float lead = m - Front;
        if (lead > 0.0) {
            return vec3(0.0);
        }
        float band = 6.0 * size + 10.0 + Front * 0.03;
        float k = clamp(-lead / band, 0.0, 1.0);
        vec3 f = fract((rel + CamOffset) / size);
        vec3 e = min(f, 1.0 - f);
        float second = max(min(e.x, e.y), min(max(e.x, e.y), e.z));
        float wire = 1.0 - smoothstep(0.03, 0.09, second);
        vec3 glow = vec3(0.78, 0.68, 1.0);
        c = mix(glow * 1.6 + c, c, smoothstep(0.0, 0.45, k));
        return c + glow * wire * (1.0 - k) * 2.2;
    }
    if (m < Front - 1.6 * size) {
        return vec3(0.0);
    }
    if (m < Front) {
        // The front's white edge, gone by the time the black has the sky too.
        return mix(c, vec3(1.0), 0.92 * (1.0 - smoothstep(220.0, 420.0, Front)));
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
        c = burst(uv, c);
        c = erase(uv, c);
        if (OdinState.x > 0.0) {
            // Nothing stands in front of it in the sky or where the black still is; elsewhere the world remade does.
            bool open = isSky(uv) || dot(c, c) < 1.0e-6;
            float him = length(Odin.xyz - CamOffset) - 0.4 * Odin.w;
            if (open || length(relAt(uv)) > him) {
                vec4 o = texture(Sampler2, uv);
                c = c * (1.0 - o.a * OdinState.x) + o.rgb * OdinState.x;
            }
        }
    }
    c = mix(c, vec3(1.0), clamp(Flash, 0.0, 1.0));
    c = mix(c, vec3(0.0), clamp(Black, 0.0, 1.0));
    fragColor = vec4(c, 1.0);
    gl_FragDepth = rawDepth(uv);
}
