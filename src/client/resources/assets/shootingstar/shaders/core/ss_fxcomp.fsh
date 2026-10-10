#version 150

// Lays the HDR effects buffer (premultiplied: fire, plasma, smoke) and its bloom over the world,
// rolling the light off smoothly instead of clipping it.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform float BloomStrength;
uniform float WideStrength;
uniform float StreakStrength;
uniform float Dirt;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Smudges and specks on the lens: invisible until bright light floods across them.
float lensDirt(vec2 uv) {
    vec2 p = uv * vec2(1.78, 1.0);
    float blobs = smoothstep(0.62, 0.95, vnoise(p * 5.0 + 1.7)) * 0.7;
    float specks = smoothstep(0.82, 0.97, vnoise(p * 23.0 + 9.3)) * 0.5;
    float smear = smoothstep(0.55, 0.9, vnoise(vec2(p.x * 2.2 + p.y * 0.8, p.y * 7.0) + 4.1)) * 0.35;
    return blobs + specks + smear;
}

float shoulder(float x) {
    const float k = 0.72;
    return x < k ? x : k + (1.0 - k) * (1.0 - exp(-(x - k) / (1.0 - k)));
}

void main() {
    vec4 fx = texture(Sampler0, texCoord);
    // Anamorphic streaks: the brightest points smeared sideways in a cold blue, like a cinema lens.
    vec3 light = max(fx.rgb, 0.0) + texture(Sampler1, texCoord).rgb * BloomStrength
        + texture(Sampler2, texCoord).rgb * WideStrength
        + texture(Sampler3, texCoord).rgb * StreakStrength * vec3(0.55, 0.72, 1.0)
        + texture(Sampler2, texCoord).rgb * lensDirt(texCoord) * Dirt;
    float peak = max(max(light.r, light.g), light.b);
    vec3 c = light;
    if (peak > 0.0) {
        float mapped = shoulder(peak);
        c = light * (mapped / peak);
        c = mix(c, vec3(mapped), 1.0 - exp(-max(peak - 1.0, 0.0) * 0.35));
    }
    fragColor = vec4(c, clamp(fx.a, 0.0, 1.0));
}
