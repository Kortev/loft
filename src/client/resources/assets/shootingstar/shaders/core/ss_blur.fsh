#version 150

// Nine-tap gaussian in five linearly filtered fetches.

uniform sampler2D Sampler0;
uniform vec2 Direction;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, texCoord).rgb * 0.2270270270;
    c += texture(Sampler0, texCoord + Direction * 1.3846153846).rgb * 0.3162162162;
    c += texture(Sampler0, texCoord - Direction * 1.3846153846).rgb * 0.3162162162;
    c += texture(Sampler0, texCoord + Direction * 3.2307692308).rgb * 0.0702702703;
    c += texture(Sampler0, texCoord - Direction * 3.2307692308).rgb * 0.0702702703;
    fragColor = vec4(c, 1.0);
}
