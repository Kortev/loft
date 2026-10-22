#version 150

// Mjölnir's impact frames: the world as the stroke freezes it, in hard cuts between drawn styles. Mode 0 is only the
// flash and grading; 1 the photographic negative, blue-white; 2 the strobe silhouette, where only the light is left and
// the rest is black with a blue rim; 3 ink on pale blue paper, the bolt a solid black stroke; 4 posterised violet.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform int Mode;
uniform float Mix;
uniform vec2 Center;
uniform vec2 ScreenSize;
uniform float Time;
uniform float ProjA;
uniform float ProjB;
uniform float Zoom;
uniform float Chroma;
uniform float Exposure;
uniform float Flash;
uniform vec3 FlashColor;
uniform float Glow;

in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.14159265;

float hash(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

float depthAt(vec2 uv) {
    float z = texture(Sampler1, uv).r * 2.0 - 1.0;
    return log(max(ProjB / (z + ProjA), 0.05));
}

float lumAt(vec2 uv) {
    return dot(texture(Sampler0, uv).rgb, vec3(0.299, 0.587, 0.114));
}

float edges(vec2 uv) {
    vec2 px = 1.0 / ScreenSize;
    float gx = 0.0;
    float gy = 0.0;
    float lx = 0.0;
    float ly = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            vec2 o = vec2(float(i), float(j)) * px * 1.5;
            float wx = float(i) * (j == 0 ? 2.0 : 1.0);
            float wy = float(j) * (i == 0 ? 2.0 : 1.0);
            float d = depthAt(uv + o);
            float l = lumAt(uv + o);
            gx += d * wx;
            gy += d * wy;
            lx += l * wx;
            ly += l * wy;
        }
    }
    return clamp(length(vec2(gx, gy)) * 3.2 + length(vec2(lx, ly)) * 1.0 - 0.17, 0.0, 1.0);
}

// Radiating lines that kink as they go, like the frame itself is cracking with current.
float cracks(vec2 uv) {
    vec2 p = (uv - Center) * vec2(ScreenSize.x / ScreenSize.y, 1.0);
    float r = length(p);
    float ang = atan(p.y, p.x) / PI;
    float kink = (hash(floor(r * 9.0) + floor(Time * 5.0)) - 0.5) * 0.02;
    float slot = floor((ang + kink) * 48.0);
    float on = step(0.8, hash(slot + floor(Time * 5.0) * 13.0));
    float thin = 1.0 - smoothstep(0.0, 0.25, abs(fract((ang + kink) * 48.0) - 0.5) * 2.0 - 0.45);
    return on * thin * smoothstep(0.15, 0.75, r);
}

void main() {
    vec2 uv = (texCoord - Center) / Zoom + Center;
    vec3 src;
    if (Chroma > 0.0) {
        vec2 off = (uv - Center) * Chroma;
        src = vec3(texture(Sampler0, uv - off).r, texture(Sampler0, uv).g, texture(Sampler0, uv + off).b);
    } else {
        src = texture(Sampler0, uv).rgb;
    }
    vec3 outColor = src;
    float lum = dot(src, vec3(0.299, 0.587, 0.114));
    if (Mode == 1) {
        float e = edges(uv);
        float inv = 1.0 - lum;
        vec3 c = mix(vec3(0.01, 0.02, 0.07), vec3(0.78, 0.9, 1.0), smoothstep(0.12, 0.92, inv));
        outColor = mix(c, vec3(0.0, 0.04, 0.12), e * 0.85) + vec3(0.55, 0.75, 1.0) * cracks(texCoord) * 0.3;
    } else if (Mode == 2) {
        float e = edges(uv);
        float bright = smoothstep(0.8, 0.88, lum);
        vec3 c = mix(vec3(0.0, 0.0, 0.015), vec3(1.0), bright);
        outColor = c + vec3(0.22, 0.45, 1.0) * e * 0.7 * (1.0 - bright);
    } else if (Mode == 3) {
        float e = edges(uv);
        float ink = max(smoothstep(0.72, 0.8, lum), e);
        outColor = mix(vec3(0.92, 0.95, 1.0), vec3(0.02, 0.03, 0.1), ink);
        outColor = mix(outColor, vec3(0.02, 0.03, 0.1), cracks(texCoord) * 0.8);
    } else if (Mode == 4) {
        float level = floor(clamp(lum * 1.1, 0.0, 0.999) * 4.0);
        vec3 c0 = vec3(0.03, 0.0, 0.1);
        vec3 c1 = vec3(0.3, 0.07, 0.6);
        vec3 c2 = vec3(0.25, 0.68, 1.0);
        vec3 c3 = vec3(0.95, 0.97, 1.0);
        outColor = level < 0.5 ? c0 : level < 1.5 ? c1 : level < 2.5 ? c2 : c3;
        outColor = mix(outColor, c0, edges(uv) * 0.8);
    }
    vec3 color = mix(src, outColor, Mix) * Exposure + vec3(0.3, 0.45, 1.0) * Glow;
    fragColor = vec4(mix(color, FlashColor, clamp(Flash, 0.0, 1.0)), 1.0);
}
