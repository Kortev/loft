#version 150

// Lays the HDR effects buffer (premultiplied: fire, plasma, smoke) and its bloom over the world,
// rolling the light off smoothly instead of clipping it.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform float BloomStrength;
uniform float WideStrength;

in vec2 texCoord;

out vec4 fragColor;

float shoulder(float x) {
    const float k = 0.72;
    return x < k ? x : k + (1.0 - k) * (1.0 - exp(-(x - k) / (1.0 - k)));
}

void main() {
    vec4 fx = texture(Sampler0, texCoord);
    vec3 light = max(fx.rgb, 0.0) + texture(Sampler1, texCoord).rgb * BloomStrength
        + texture(Sampler2, texCoord).rgb * WideStrength;
    float peak = max(max(light.r, light.g), light.b);
    vec3 c = light;
    if (peak > 0.0) {
        float mapped = shoulder(peak);
        c = light * (mapped / peak);
        c = mix(c, vec3(mapped), 1.0 - exp(-max(peak - 1.0, 0.0) * 0.35));
    }
    fragColor = vec4(c, clamp(fx.a, 0.0, 1.0));
}
