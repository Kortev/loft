#version 150

// Soft puffs of dust and steam: Mjölnir's shockwave racing out over the ground, and steam rising off the crater. Round,
// with edges torn by NoiseTex (Sampler0) and billows lighter on top than underneath; cut softly where they meet the
// world (its depth in Sampler1), and lit blue-white by the stroke (Flash). Premultiplied alpha.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 ScreenSize;
uniform float ProjA;
uniform float ProjB;
uniform float Softness;
uniform float Flash;

in vec2 corner;
in vec4 vertexColor;
in float seed;

out vec4 fragColor;

void main() {
    vec2 p = corner * 0.32 + vec2(seed * 7.13, seed * 3.71);
    float n = texture(Sampler0, p).r * 0.6 + texture(Sampler0, p * 2.3 + 0.5).g * 0.4;
    float r = length(corner);
    float shape = 1.0 - smoothstep(0.3, 1.0, r + (n - 0.5) * 0.7);
    float a = shape * clamp(vertexColor.a, 0.0, 1.0);
    if (a < 0.003) {
        discard;
    }
    float z = texture(Sampler1, gl_FragCoord.xy / ScreenSize).r * 2.0 - 1.0;
    float scene = ProjB / (z + ProjA);
    float puff = ProjB / ((gl_FragCoord.z * 2.0 - 1.0) + ProjA);
    a *= clamp((scene - puff) / Softness, 0.0, 1.0);
    // Lit from above: the top of each billow lighter, the underside in its own shadow.
    float lit = 0.62 + 0.38 * smoothstep(-0.6, 0.6, corner.y + (n - 0.5) * 0.8);
    vec3 color = vertexColor.rgb * lit * (0.8 + 0.4 * n) + vec3(0.55, 0.65, 1.0) * Flash * (0.4 + 0.6 * n);
    fragColor = vec4(color * a, a);
}
