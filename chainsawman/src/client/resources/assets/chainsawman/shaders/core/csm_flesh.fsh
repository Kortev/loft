#version 150

// The Eternity Devil's baked meshes: the paint is in the vertex colour (alpha = how much the voxel glows on its own), and the game adds
// the rest here: a floor of the world's light so the grey-brown flesh always reads, an orange-red rim on edges that turn away from the eye,
// a faint red light from the pit (GlowPos, in view space), and a flash when it is hit.

in vec4 vColor;
in vec3 vN;
in vec3 vP;

uniform vec4 Tint;
uniform vec4 GlowPos;
uniform vec3 GlowCol;
uniform vec3 RimCol;
uniform float RimAmt;
uniform vec4 Clip;

out vec4 fragColor;

void main() {
    if (dot(vP, Clip.xyz) + Clip.w < 0.0) {
        discard;
    }
    vec3 n = normalize(vN);
    vec3 v = normalize(-vP);
    float nv = clamp(abs(dot(n, v)), 0.0, 1.0);
    float rim = smoothstep(0.35, 0.0, nv) * RimAmt;
    vec3 base = vColor.rgb * Tint.rgb;
    vec3 toG = GlowPos.xyz - vP;
    float d = max(length(toG), 0.001);
    float facing = 0.35 + 0.65 * max(dot(n, toG / d), 0.0);
    float fall = 1.0 / (1.0 + d * d / max(GlowPos.w * GlowPos.w, 0.01));
    vec3 col = base + RimCol * rim * 0.7 + GlowCol * facing * fall * (0.2 + dot(base, vec3(0.5)));
    col = mix(col, vColor.rgb * 1.15, vColor.a);
    col += vec3(0.55, 0.12, 0.05) * Tint.a;
    fragColor = vec4(col, 1.0);
}
