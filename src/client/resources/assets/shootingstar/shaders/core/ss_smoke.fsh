#version 150

uniform sampler2D Sampler1;
uniform vec2 ScreenSize;
uniform float ProjA;
uniform float ProjB;
uniform float Softness;

in vec2 corner;
in vec4 vertexColor;
in float viewDepth;
in float seed;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    return noise(p) * 0.5 + noise(p * 2.1 + 3.7) * 0.3 + noise(p * 4.3 + 9.1) * 0.2;
}

void main() {
    vec2 p = corner + seed * 13.0;
    float r = length(corner);
    float shape = smoothstep(1.0, 0.25, r + (fbm(p * 1.7) - 0.5) * 0.7);
    if (shape <= 0.002) {
        discard;
    }
    float z = texture(Sampler1, gl_FragCoord.xy / ScreenSize).r * 2.0 - 1.0;
    float scene = ProjB / (z + ProjA);
    float soft = clamp((scene - viewDepth) / Softness, 0.0, 1.0);
    float shade = 0.75 + 0.35 * fbm(p * 3.0);
    float a = shape * vertexColor.a * soft;
    fragColor = vec4(vertexColor.rgb * shade * a, a);
}
