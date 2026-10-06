#version 150

// See ss_tree.vsh. Added to what is behind.

in vec3 light;
in vec2 corner;
flat in int kind;

out vec4 fragColor;

void main() {
    float r2 = dot(corner, corner);
    vec3 c;
    if (kind == 3) {
        c = light * exp(-r2 * 3.0);
    } else if (kind == 2) {
        // A world: a hard bright core in a wide soft glow.
        c = light * (exp(-r2 * 30.0) * 2.0 + exp(-r2 * 4.0) * 0.4);
    } else {
        c = light * exp(-r2 * 4.5) * 1.1;
    }
    c *= 1.0 - smoothstep(0.6, 1.0, sqrt(r2));
    fragColor = vec4(c, 0.0);
}
