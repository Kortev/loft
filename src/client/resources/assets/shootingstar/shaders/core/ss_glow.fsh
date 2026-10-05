#version 150

// Additive light shapes. UV0 spans -1..1 across the sprite (beams: x across, y along 0..1).

uniform int Mode;
uniform float Param;
uniform float Progress;
uniform vec3 Tint;

in vec2 uv;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float a;
    if (Mode == 0) {
        // Soft light with an optional wide halo.
        float r2 = dot(uv, uv);
        a = exp(-r2 * 9.0) + exp(-r2 * 2.2) * 0.35 * Param;
        a *= 1.0 - smoothstep(0.85, 1.0, sqrt(r2));
    } else if (Mode == 1) {
        // Beam: gaussian across, fading at both ends.
        a = exp(-uv.x * uv.x * 6.0) * smoothstep(0.0, 0.08, uv.y) * smoothstep(1.0, 0.85, uv.y);
    } else if (Mode == 2) {
        // Thin ring of radius 1 and width Param.
        float r = length(uv);
        a = exp(-pow((r - 1.0 + Param) / Param, 2.0));
    } else if (Mode == 3) {
        // Anamorphic streak.
        a = exp(-uv.y * uv.y * 30.0) * (1.0 - abs(uv.x)) * (1.0 - abs(uv.x));
    } else if (Mode == 4) {
        // Ring ribbon that lights up as Progress sweeps round (x = 0..1 around, y = -1..1 across).
        float on = smoothstep(Progress, Progress - 0.012, uv.x);
        float head = exp(-pow((uv.x - Progress) * 70.0, 2.0)) * step(0.001, Progress) * step(Progress, 0.999);
        // Every coil station is a point of light; between them the ring is a fainter thread.
        float node = pow(0.5 + 0.5 * cos(uv.x * 6.2831853 * 720.0), 24.0);
        a = exp(-uv.y * uv.y * 5.0) * (Param + on * 0.55 + head * 3.0) + on * 2.2 * node * exp(-uv.y * uv.y * 40.0);
    } else if (Mode == 8) {
        // A curtain of light standing on its lower edge (y = -1): even along it, fading upwards, soft at the ends.
        float up = (uv.y + 1.0) * 0.5;
        a = (1.0 - pow(abs(uv.x), 10.0)) * (exp(-up * 4.0) * 0.7 + exp(-up * 22.0) * 1.6);
    } else if (Mode == 7) {
        // Line: even along its length and soft across, so beams laid end to end draw one unbroken line.
        a = exp(-uv.x * uv.x * 5.0);
    } else if (Mode == 6) {
        // Drawn streak: a hard-edged taper with a white-hot core, like the impact frames' speed lines.
        float halfWidth = 0.75 * (1.0 - abs(uv.x));
        float w = fwidth(uv.y) * 1.2 + 1.0e-4;
        float body = 1.0 - smoothstep(halfWidth - w, halfWidth + w, abs(uv.y));
        float core = 1.0 - smoothstep(halfWidth * 0.4 - w, halfWidth * 0.4 + w, abs(uv.y));
        a = body * (0.55 + 0.9 * core);
    } else {
        // Star with four diffraction spikes.
        float r2 = dot(uv, uv);
        float core = exp(-r2 * 60.0) * 2.0 + exp(-r2 * 9.0) * 0.6;
        float spikes = exp(-abs(uv.y) * 90.0) * (1.0 - abs(uv.x)) + exp(-abs(uv.x) * 90.0) * (1.0 - abs(uv.y));
        a = core + spikes * 0.8;
    }
    fragColor = vec4(vertexColor.rgb * Tint * vertexColor.a * a, 1.0);
}
