#version 150

// Hollow Purple's full-screen pass over the world. Positions are relative to the camera.
//
// Three bodies of light, each a ray-marched ball with a hard white core, drawn over the finished world with the depth
// kept (so a hill in front hides them): Blue at one hand (pulling, its rings drawing in), Red at the other (pushing, its
// rings thrown out), and when they meet the violet sphere, huge, its skin churning, arcs crawling over it, a streak
// of light behind it as it flies. Each lights the world round it (the ground, the trees, his face, a village's walls) in
// its own colour and bends the picture round its edge. At the merge a shell of white-violet shoots out and is gone.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 InvViewProj;
uniform vec2 ScreenSize;
uniform float Time;
// Each body: where it is (xyz, relative to the camera) and its radius (w); and where it is on the screen (xy, uv), how big
// (z, its radius in uv-heights).
uniform vec4 Sphere;
uniform vec4 SphereUv;
uniform vec4 Blue;
uniform vec4 BlueUv;
uniform vec4 Red;
uniform vec4 RedUv;
// How strong each is (0 to 1): Blue, Red, the sphere; and the sphere's collapse (0 to 1, the flash at its end).
uniform vec4 Amt;
// Where the two met (xyz, relative to the camera), and the seconds since (negative: not yet).
uniform vec4 Merge;
// The way the sphere flies (xyz) and how fast it is going (w, 0 to 1).
uniform vec4 Fly;

in vec2 texCoord;

out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
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

// The picture bent round a body's edge: pulled in towards it (sign +1) or thrown out from it (-1).
vec2 lens(vec2 uv, vec4 at, float sign, float amt) {
    if (amt <= 0.0 || at.z <= 0.0) {
        return vec2(0.0);
    }
    float aspect = ScreenSize.x / ScreenSize.y;
    vec2 q = (uv - at.xy) * vec2(aspect, 1.0);
    float r = length(q) / at.z;
    if (r < 1.0 || r > 5.0) {
        return vec2(0.0);
    }
    float bend = amt * at.z * 0.5 * exp(-(r - 1.0) * 1.3) / r;
    return -normalize(q) / vec2(aspect, 1.0) * bend * sign;
}

// Light cast on the world by a body: its colour, falling off with distance, brighter on what faces it.
vec3 lightFrom(vec3 rel, vec3 n, vec3 base, vec3 C, float R, vec3 col, float k) {
    vec3 d = C - rel;
    float l = length(d);
    float att = k * (R * R * 5.0) / (l * l + R * R * 5.0);
    att *= smoothstep(R * 22.0, R * 4.0, l);
    float lam = 0.5 + 0.5 * dot(n, d / max(l, 1.0e-3));
    return (base * 1.5 + 0.04) * col * att * lam * 2.0;
}

// A ball of light: halo, then (if the ray meets it before the world) its churning skin, hard core and arcs.
// Returns its light (rgb) and how much it hides what is behind it (a).
vec4 orb(vec3 C, float R, vec3 dir, float dist, vec3 deep, vec3 mid, vec3 hot, float amt, float churn, float coreSize) {
    float b = dot(C, dir);
    if (b <= 0.0 || amt <= 0.0) {
        return vec4(0.0);
    }
    float d2 = max(dot(C, C) - b * b, 0.0);
    float vis = 1.0 - smoothstep(dist - R * 0.8, dist + R * 0.4, b);
    vec3 e = mid * (exp(-d2 / (R * R * 2.4)) * 0.42 + exp(-d2 / (R * R * 16.0)) * 0.2);
    float a = 0.0;
    float disc = R * R - d2;
    if (disc > 0.0) {
        float t0 = b - sqrt(disc);
        if (t0 < dist) {
            vec3 n = (dir * t0 - C) / R;
            float d = sqrt(d2) / R;
            float fr = pow(1.0 - clamp(dot(n, -dir), 0.0, 1.0), 2.4);
            float swirl = fbm(n * 3.2 + vec3(Time * churn, Time * churn * 0.6, Time * churn * 0.3));
            float arcs = pow(1.0 - abs(noise(n * 6.5 + Time * vec3(8.0, 6.0, 5.0)) * 2.0 - 1.0), 16.0);
            float core = smoothstep(1.0, 0.0, d / coreSize);
            vec3 s = mix(deep, mid, swirl) * (0.7 + 0.7 * swirl);
            s += hot * pow(core, 2.4) * 2.6;
            s += mid * fr * 1.2 + hot * arcs * 1.0;
            e = s + e * 0.3;
            a = 1.0;
        }
    }
    return vec4(e * vis * amt, a * vis * amt);
}

// A thin shell of light of radius rr round C, seen from the camera, brightest at its limb.
vec3 shell(vec3 C, float rr, float width, vec3 dir, float dist, vec3 col) {
    float b = dot(C, dir);
    if (b <= 0.0) {
        return vec3(0.0);
    }
    float d = sqrt(max(dot(C, C) - b * b, 0.0));
    float vis = 1.0 - smoothstep(dist - rr * 0.5, dist + rr * 0.5, b);
    float x = (d - rr) / width;
    float limb = exp(-x * x);
    // A faint fill inside it, so a shell passing over the camera's side of a thing lights it.
    float fill = smoothstep(rr, rr * 0.3, d) * 0.06;
    return col * (limb + fill) * vis;
}

void main() {
    vec2 uv = texCoord;
    float depth = rawDepth(uv);
    bool sky = depth >= 0.99999;
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 far4 = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec3 dir = normalize(far4.xyz / far4.w);
    vec4 w4 = InvViewProj * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    vec3 rel = sky ? dir * 100000.0 : w4.xyz / w4.w;
    float dist = sky ? 1.0e9 : length(rel);
    vec3 fn = normalize(cross(dFdx(rel), dFdy(rel)));
    vec3 n = dot(fn, rel) > 0.0 ? -fn : fn;

    float blueAmt = Amt.x;
    float redAmt = Amt.y;
    float sphereAmt = Amt.z;
    float collapse = Amt.w;

    // The picture bent round each body's edge.
    vec2 wuv = uv + lens(uv, BlueUv, 1.0, blueAmt) + lens(uv, RedUv, -1.0, redAmt) + lens(uv, SphereUv, 1.0, sphereAmt * 0.8);
    vec3 c = scene(wuv);
    // The world goes dim under it: imaginary mass, light drained, so the violet stands out.
    c *= 1.0 - 0.38 * sphereAmt * (1.0 - collapse);

    // Their light on the world.
    if (!sky) {
        vec3 base = c;
        if (blueAmt > 0.0) {
            c += lightFrom(rel, n, base, Blue.xyz, Blue.w, vec3(0.15, 0.45, 1.0), blueAmt * 1.4);
        }
        if (redAmt > 0.0) {
            c += lightFrom(rel, n, base, Red.xyz, Red.w, vec3(1.0, 0.16, 0.08), redAmt * 1.4);
        }
        if (sphereAmt > 0.0) {
            c += lightFrom(rel, n, base, Sphere.xyz, Sphere.w, vec3(0.42, 0.1, 1.0), sphereAmt * 1.7);
        }
    }

    vec3 e = vec3(0.0);
    float hide = 0.0;

    // Blue: a cold electric ball, its rings drawing in.
    if (blueAmt > 0.0) {
        vec4 o = orb(Blue.xyz, Blue.w, dir, dist, vec3(0.04, 0.2, 0.9), vec3(0.2, 0.55, 1.0), vec3(0.8, 0.95, 1.0), blueAmt, 3.0, 0.62);
        e += o.rgb;
        hide = max(hide, o.a);
        for (int i = 0; i < 3; i++) {
            float ph = fract(Time * 1.1 + float(i) / 3.0);
            float rr = Blue.w * (1.3 + (1.0 - ph) * 9.0);
            e += shell(Blue.xyz, rr, Blue.w * 0.16 * (1.0 + (1.0 - ph)), dir, dist, vec3(0.2, 0.55, 1.0)) * ph * ph * 0.7 * blueAmt;
        }
    }
    // Red: a violent ball throwing shock rings out.
    if (redAmt > 0.0) {
        vec4 o = orb(Red.xyz, Red.w, dir, dist, vec3(0.5, 0.02, 0.02), vec3(1.0, 0.18, 0.06), vec3(1.0, 0.85, 0.6), redAmt, 5.0, 0.6);
        e += o.rgb;
        hide = max(hide, o.a);
        for (int i = 0; i < 3; i++) {
            float ph = fract(Time * 1.5 + float(i) / 3.0);
            float rr = Red.w * (1.3 + ph * 11.0);
            e += shell(Red.xyz, rr, Red.w * 0.16 * (1.0 + ph), dir, dist, vec3(1.0, 0.3, 0.12)) * (1.0 - ph) * (1.0 - ph) * 0.9 * redAmt;
        }
    }
    // The meeting: a shell of white-violet thrown out, and gone.
    if (Merge.w >= 0.0) {
        float w = Merge.w;
        float fade = exp(-w * 2.6);
        e += shell(Merge.xyz, 3.0 + w * 46.0, 0.6 + w * 3.0, dir, dist, vec3(0.7, 0.5, 1.0)) * fade * 1.1;
        e += shell(Merge.xyz, 2.0 + w * 24.0, 0.4 + w * 1.6, dir, dist, vec3(0.5, 0.3, 1.0)) * fade * 0.7;
        float bm = dot(Merge.xyz, dir);
        if (bm > 0.0) {
            float dm = dot(Merge.xyz, Merge.xyz) - bm * bm;
            e += vec3(0.95, 0.85, 1.0) * exp(-dm / (3.0 + w * 14.0)) * exp(-w * 6.0) * 1.6;
        }
    }
    // The sphere: huge, violet, a hard white core, and a streak of light behind it as it flies.
    if (sphereAmt > 0.0) {
        float R = Sphere.w;
        vec3 fly = normalize(Fly.xyz);
        float flash = exp(-pow((collapse - 0.25) * 6.0, 2.0)) * 5.0;
        vec4 o = orb(Sphere.xyz, R, dir, dist, vec3(0.24, 0.03, 0.72), vec3(0.5, 0.13, 1.0), vec3(0.96, 0.9, 1.0), sphereAmt, 1.8, 0.36);
        e += o.rgb * (1.0 + flash);
        hide = max(hide, o.a);
        // The streak: haloes strung out behind it.
        for (int k = 1; k <= 7; k++) {
            float f = float(k);
            vec3 T = Sphere.xyz - fly * R * (0.8 + f * 1.25) * Fly.w;
            float bt = dot(T, dir);
            if (bt > 0.0) {
                float dt = max(dot(T, T) - bt * bt, 0.0);
                float vt = 1.0 - smoothstep(dist - R, dist + R * 0.5, bt);
                float rk = R * (1.05 - 0.09 * f);
                e += vec3(0.5, 0.18, 1.0) * exp(-dt / (rk * rk * 1.6)) * vt * (0.6 - 0.07 * f) * sphereAmt * Fly.w;
            }
        }
        e += vec3(0.55, 0.2, 1.0) * shell(Sphere.xyz, R * (1.0 + 7.0 * collapse), R * 0.25 * (1.0 + collapse * 4.0), dir, dist, vec3(1.0)) * collapse * (1.0 - collapse) * 3.0;
        e += vec3(0.9, 0.8, 1.0) * flash * 0.15 * exp(-pow(length(cross(Sphere.xyz, dir)) / (R * 3.0), 2.0)) * step(0.0, dot(Sphere.xyz, dir));
    }

    // Light that saturates: its colour holds until it is bright, and then goes to white.
    e = vec3(1.0) - exp(-e * 1.25);
    c = c * (1.0 - hide * 0.95) + e;
    c = min(c, vec3(1.0));
    fragColor = vec4(c, 1.0);
    gl_FragDepth = depth;
}
