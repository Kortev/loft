#version 150

// Impact frames: the rendered world turned into stylised comic frames, as in the reel.
// Mode 0 shock distortion only, 1 red edge lines on black, 2 inverted cyan, 3 posterised orange,
// 4 orange halftone, 5 white with black ink outlines, 6 stark inverted black and white, 7 red and black.

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
uniform float Warp;
uniform float WarpRadius;
uniform float Chroma;
uniform float Darken;
uniform float Exposure;
uniform vec3 Tint;
uniform float Flash;
uniform vec3 FlashColor;
uniform vec2 HazeCenter;
uniform vec2 HazeSize;
uniform float Haze;
uniform vec2 TrailA;
uniform vec2 TrailB;
uniform float TrailWidth;
uniform float Trail;

in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.14159265;

float hash(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), f.x), mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), f.x), f.y);
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
    // Silhouettes (depth) carry the drawing; block textures only add a little hatching.
    return clamp(length(vec2(gx, gy)) * 3.2 + length(vec2(lx, ly)) * 1.05 - 0.17, 0.0, 1.0);
}

float speedLines(vec2 uv) {
    vec2 p = (uv - Center) * vec2(ScreenSize.x / ScreenSize.y, 1.0);
    float ang = atan(p.y, p.x) / PI;
    float r = length(p);
    float slot = floor(ang * 64.0);
    float on = step(0.72, hash(slot + floor(Time * 6.0) * 7.0));
    float taper = smoothstep(0.12, 0.7, r);
    float thin = 1.0 - smoothstep(0.0, 0.35, abs(fract(ang * 64.0) - 0.5) * 2.0 - 0.3);
    return on * taper * thin;
}

float halftone(vec2 uv, float value) {
    vec2 p = uv * ScreenSize / 6.0;
    p = mat2(0.7071, -0.7071, 0.7071, 0.7071) * p;
    vec2 cell = fract(p) - 0.5;
    float r = sqrt(clamp(value, 0.0, 1.0)) * 0.64;
    return smoothstep(r + 0.08, r - 0.08, length(cell));
}

void main() {
    vec2 uv = (texCoord - Center) / Zoom + Center;
    if (Warp > 0.0) {
        // Refraction ring of the shock front, in screen space around the impact.
        vec2 p = (uv - Center) * vec2(ScreenSize.x / ScreenSize.y, 1.0);
        float r = length(p);
        float k = exp(-pow((r - WarpRadius) * 18.0, 2.0));
        uv -= normalize(p + 1.0e-5) * k * Warp / vec2(ScreenSize.x / ScreenSize.y, 1.0);
    }
    if (Trail > 0.0) {
        // The round's shock cone: the air along its path bends the light, in a wake that widens behind the head
        // (TrailA) and shimmers.
        vec2 asp = vec2(ScreenSize.x / ScreenSize.y, 1.0);
        vec2 pa = (uv - TrailA) * asp;
        vec2 ba = (TrailB - TrailA) * asp;
        float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1.0e-6), 0.0, 1.0);
        vec2 off = pa - ba * h;
        float d = length(off);
        float width = TrailWidth * (0.25 + 2.5 * h);
        float edge = exp(-pow((d - width * 0.6) / (width * 0.5), 2.0));
        float k = edge * (1.0 - h) * smoothstep(0.0, 0.03, h + d);
        vec2 n = off / max(d, 1.0e-5);
        float ripple = vnoise(uv * 70.0 + vec2(Time * 0.35, -Time * 0.27)) - 0.5;
        uv -= (n * 0.7 + vec2(ripple, -ripple) * 0.6) * k * Trail / asp;
    }
    if (Haze > 0.0) {
        // Heat shimmer over the molten bowl: the picture wobbles in rising, flowing cells.
        vec2 q = (uv - HazeCenter) / max(HazeSize, vec2(1.0e-4));
        float shape = exp(-(q.x * q.x * 1.4 + q.y * q.y));
        float t = Time * 0.05;
        vec2 w = vec2(vnoise(uv * vec2(34.0, 20.0) + vec2(0.0, -t * 3.0)),
                      vnoise(uv * vec2(28.0, 17.0) + vec2(5.2, -t * 3.7))) - 0.5;
        uv += w * Haze * shape;
    }
    vec3 src;
    if (Chroma > 0.0) {
        vec2 off = (uv - Center) * Chroma;
        src = vec3(texture(Sampler0, uv - off).r, texture(Sampler0, uv).g, texture(Sampler0, uv + off).b);
    } else {
        src = texture(Sampler0, uv).rgb;
    }
    src *= 1.0 - Darken;
    vec3 outColor = src;
    float lum = dot(src, vec3(0.299, 0.587, 0.114));
    if (Mode == 1) {
        float e = edges(uv);
        vec3 red = vec3(1.0, 0.16, 0.08);
        outColor = red * e + red * speedLines(texCoord) * 0.8 + vec3(1.0, 0.85, 0.7) * smoothstep(0.92, 1.0, lum);
    } else if (Mode == 2) {
        float e = edges(uv);
        float inv = 1.0 - lum;
        vec3 ink = mix(vec3(0.02, 0.1, 0.16), vec3(0.75, 0.97, 1.0), smoothstep(0.25, 0.75, inv));
        outColor = mix(ink, vec3(0.0, 0.05, 0.08), e * 0.9) + vec3(0.6, 0.95, 1.0) * speedLines(texCoord) * 0.35;
    } else if (Mode == 3) {
        float level = floor(clamp(lum * 1.15, 0.0, 0.999) * 4.0);
        vec3 c0 = vec3(0.06, 0.01, 0.0);
        vec3 c1 = vec3(0.55, 0.08, 0.02);
        vec3 c2 = vec3(1.0, 0.36, 0.06);
        vec3 c3 = vec3(1.0, 0.86, 0.6);
        outColor = level < 0.5 ? c0 : level < 1.5 ? c1 : level < 2.5 ? c2 : c3;
        outColor = mix(outColor, c0, edges(uv) * 0.8);
    } else if (Mode == 4) {
        float ink = halftone(texCoord, 1.0 - lum);
        vec3 paper = vec3(1.0, 0.93, 0.8);
        vec3 orange = vec3(0.95, 0.3, 0.05);
        outColor = mix(paper, orange, ink);
        outColor = mix(outColor, vec3(0.15, 0.02, 0.0), edges(uv) * 0.85);
        outColor += vec3(1.0, 0.95, 0.85) * speedLines(texCoord) * 0.3;
    } else if (Mode == 5) {
        float e = edges(uv);
        outColor = mix(vec3(1.0, 0.98, 0.95), vec3(0.05, 0.04, 0.05), e) + vec3(0.0, 0.0, 0.0) * speedLines(texCoord);
        outColor = mix(outColor, vec3(0.1, 0.03, 0.02), speedLines(texCoord) * 0.7);
    } else if (Mode == 6) {
        // Stark black and white, the way a manga draws the blast: the light turns to solid black, the rest to bare
        // paper, a ragged edge between them and the ink lines in the opposite tone.
        float e = edges(uv);
        float dark = smoothstep(0.4, 0.48, lum + (vnoise(uv * ScreenSize / 7.0) - 0.5) * 0.1);
        vec3 c = mix(vec3(0.97, 0.96, 0.93), vec3(0.02), dark);
        c = mix(c, vec3(0.99) - c, e);
        outColor = mix(c, vec3(0.02), speedLines(texCoord) * (1.0 - dark) * 0.85);
    } else if (Mode == 7) {
        // Red and black: the blast a red silhouette round a white-hot core, everything else black, white speed lines.
        float e = edges(uv);
        vec3 c = mix(vec3(0.02, 0.0, 0.0), vec3(0.88, 0.06, 0.03), smoothstep(0.24, 0.32, lum));
        c = mix(c, vec3(1.0, 0.95, 0.88), smoothstep(0.78, 0.86, lum));
        c = mix(c, vec3(0.0), e * 0.85);
        outColor = c + vec3(1.0, 0.92, 0.86) * speedLines(texCoord) * 0.55;
    }
    vec3 color = mix(src, outColor, Mix) * Exposure + Tint;
    fragColor = vec4(mix(color, FlashColor, clamp(Flash, 0.0, 1.0)), 1.0);
}
