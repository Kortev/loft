#version 150

// The void's sky, painted into a panorama (every direction, longitude across, latitude down) so the Domain's pass can
// look any direction up in it, instead of working it out again for every pixel and every reflection: its smooth, costly
// parts only: endless dark, nebulae (bent round the great eye), light flowing past in slow rivers curving round it, and
// the eye's soft glow. What is fine and sharp (the stars, the eye's black disc, its ring of fire, the band across it) is
// drawn by the pass itself, where it can be sharp.

uniform float Time;
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
    for (int i = 0; i < 4; i++) {
        s += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

void basis(vec3 axis, out vec3 t1, out vec3 t2) {
    vec3 up = abs(axis.y) < 0.95 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    t1 = normalize(cross(axis, up));
    t2 = cross(axis, t1);
}

// Light flowing past in slow rivers, curving round the eye as they run in towards it: many faint threads, a few bright.
float rivers(vec3 d) {
    vec3 t1;
    vec3 t2;
    basis(Eye, t1, t2);
    float along = dot(d, Eye);
    float ang = atan(dot(d, t2), dot(d, t1));
    float twist = ang + (1.0 - along) * 1.4 + Time * 0.03;
    float lane = twist * 26.0 / PI;
    float id = floor(lane);
    float f = fract(lane);
    float width = 0.12 + 0.25 * hash(id);
    float thread = smoothstep(width, 0.0, abs(f - 0.5)) * step(0.45, hash(id + 13.0));
    float run = fract(along * (1.2 + 1.5 * hash(id)) + Time * (0.02 + 0.04 * hash(id + 7.0)) + hash(id + 11.0));
    float pulse = smoothstep(0.0, 0.35, run) * smoothstep(1.0, 0.5, run);
    float wide = smoothstep(0.75, 0.2, abs(fbm(vec3(twist * 2.0, along * 3.0, Time * 0.02)) - 0.5) * 4.0);
    return thread * pulse * (0.25 + 0.75 * hash(id + 17.0)) + 0.12 * wide * (1.0 - abs(along));
}

void main() {
    // The direction this texel stands for: longitude across (from -pi), latitude down (from straight up).
    float lon = (texCoord.x - 0.5) * 2.0 * PI;
    float lat = (1.0 - texCoord.y) * PI;
    vec3 d = vec3(sin(lat) * cos(lon), cos(lat), sin(lat) * sin(lon));
    // Light passing close to the eye is bent out round it.
    float a = acos(clamp(dot(d, Eye), -1.0, 1.0));
    float r = 0.21;
    vec3 side = d - Eye * dot(d, Eye);
    float sl = length(side);
    vec3 t = sl > 1.0e-4 ? side / sl : vec3(0.0, 1.0, 0.0);
    float a2 = a + 0.012 / max(a - r * 0.9, 0.01);
    vec3 bent = normalize(Eye * cos(a2) + t * sin(a2));

    vec3 c = mix(vec3(0.004, 0.006, 0.02), vec3(0.012, 0.02, 0.06), 0.5 + 0.5 * bent.y);
    float neb = fbm(bent * 2.4 + vec3(0.0, 0.0, Time * 0.004));
    float wisp = fbm(bent * 5.5 + neb * 2.0);
    c += vec3(0.04, 0.09, 0.2) * smoothstep(0.45, 0.85, neb) + vec3(0.14, 0.05, 0.2) * smoothstep(0.6, 0.95, wisp) * 0.6;
    c += vec3(0.55, 0.78, 1.0) * rivers(bent) * 0.5;
    // The eye's soft glow, swirling.
    vec3 t1;
    vec3 t2;
    basis(Eye, t1, t2);
    float ang = atan(dot(d, t2), dot(d, t1));
    float swirl = 0.7 + 0.5 * noise(vec3(ang * 3.0 - Time * 0.9, a * 25.0, Time * 0.2));
    float glow = (exp(-max(a - r, 0.0) * 10.0) * 0.6 + exp(-max(a - r, 0.0) * 2.6) * 0.18) * swirl;
    c += mix(vec3(0.45, 0.72, 1.0), vec3(0.78, 0.62, 1.0), 0.3) * glow * step(r, a);
    fragColor = vec4(c, 1.0);
}
