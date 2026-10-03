#version 150

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
    c *= 0.25;
    fragColor = vec4(max(c - Threshold, 0.0) / max(1.0 - Threshold, 0.001), 1.0);
}
