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

in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.14159265;

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

// The mirror universe is drawn in cyans; nothing in ours is quite that colour.
bool isOther(vec3 c) {
    float hi = max(c.r, max(c.g, c.b));
    float lo = min(c.r, min(c.g, c.b));
    return c.b > c.r + 0.12 && c.g > c.r + 0.08 && hi - lo > 0.18;
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
    if (style == 4) {
        return vec3(1.0) - c;
    }
    bool sky = isSky(uv);
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
    for (int i = 0; i < 14; i++) {
        float fi = float(i);
        float base = (fi + 0.6 * hash(fi + Seed)) / 14.0 * 2.0 * PI;
        float steps = r * 7.0;
        float seg = floor(steps);
        float drift = mix(hash(seg + fi * 13.0 + Seed) - 0.5, hash(seg + 1.0 + fi * 13.0 + Seed) - 0.5, fract(steps)) * 0.5;
        float da = abs(mod(a - base - drift + PI, 2.0 * PI) - PI);
        float width = (0.0035 + 0.006 * (1.0 - min(r, 1.0))) / r;
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
        c = erase(uv, c);
    }
    c = mix(c, vec3(1.0), clamp(Flash, 0.0, 1.0));
    c = mix(c, vec3(0.0), clamp(Black, 0.0, 1.0));
    fragColor = vec4(c, 1.0);
}
