#version 150

// Feed finish: HDR scene plus bloom, exposure, a hue-preserving filmic roll-off, then zoom blur,
// chromatic fringe, vignette, scanlines, grain and flashes.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform float BloomStrength;
uniform float StreakStrength;
uniform float WideStrength;
uniform float Exposure;
uniform float Vignette;
uniform float Grain;
uniform float Time;
uniform float Aberration;
uniform float Flash;
uniform vec3 FlashColor;
uniform float Scanlines;
uniform float Fade;
uniform vec2 ScreenSize;
uniform float ZoomBlur;
uniform float Saturation;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 hdr(vec2 uv) {
    vec3 c;
    if (Aberration > 0.0) {
        vec2 off = (uv - 0.5) * Aberration;
        c = vec3(texture(Sampler0, uv - off).r, texture(Sampler0, uv).g, texture(Sampler0, uv + off).b);
    } else {
        c = texture(Sampler0, uv).rgb;
    }
    return c + texture(Sampler1, uv).rgb * BloomStrength + texture(Sampler2, uv).rgb * WideStrength
        + texture(Sampler3, uv).rgb * StreakStrength * vec3(0.55, 0.72, 1.0);
}

// Linear up to the knee, then an exponential shoulder towards 1.
float shoulder(float x) {
    const float k = 0.72;
    return x < k ? x : k + (1.0 - k) * (1.0 - exp(-(x - k) / (1.0 - k)));
}

vec3 tonemap(vec3 c) {
    c = max(c * Exposure, 0.0);
    float peak = max(max(c.r, c.g), c.b);
    if (peak <= 0.0) {
        return c;
    }
    float mapped = shoulder(peak);
    vec3 result = c * (mapped / peak);
    // Light far past white burns out towards white, like film.
    float burn = 1.0 - exp(-max(peak - 1.0, 0.0) * 0.35);
    return mix(result, vec3(mapped), burn);
}

void main() {
    vec2 uv = texCoord;
    vec3 color;
    if (ZoomBlur > 0.0) {
        color = vec3(0.0);
        for (int i = 0; i < 12; i++) {
            float k = 1.0 - ZoomBlur * float(i) / 11.0;
            color += hdr((uv - 0.5) * k + 0.5);
        }
        color /= 12.0;
    } else {
        color = hdr(uv);
    }
    color = tonemap(color);
    float grey = dot(color, vec3(0.299, 0.587, 0.114));
    color = clamp(mix(vec3(grey), color, Saturation), 0.0, 1.0);
    vec2 d = uv - 0.5;
    color *= 1.0 - Vignette * dot(d, d) * 2.4;
    color *= 1.0 - Scanlines * 0.08 * step(0.5, fract(gl_FragCoord.y / 3.0));
    color += (hash(floor(uv * ScreenSize) + floor(Time * 24.0) * 17.0) - 0.5) * Grain;
    color = mix(color, FlashColor, clamp(Flash, 0.0, 1.0));
    fragColor = vec4(color * Fade, 1.0);
}
