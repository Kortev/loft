#version 150

// Bloom source: the light above Threshold (HDR), with a soft knee so the glow fades in smoothly.

uniform sampler2D Sampler0;
uniform float Threshold;
uniform vec2 TexelSize;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, texCoord + TexelSize * vec2(-0.5, -0.5)).rgb
           + texture(Sampler0, texCoord + TexelSize * vec2(0.5, -0.5)).rgb
           + texture(Sampler0, texCoord + TexelSize * vec2(-0.5, 0.5)).rgb
           + texture(Sampler0, texCoord + TexelSize * vec2(0.5, 0.5)).rgb;
    c = max(c * 0.25, 0.0);
    float peak = max(max(c.r, c.g), c.b);
    float knee = Threshold * 0.5;
    float soft = clamp(peak - Threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1.0e-4);
    float contribution = max(soft, peak - Threshold) / max(peak, 1.0e-4);
    fragColor = vec4(c * contribution, 1.0);
}
