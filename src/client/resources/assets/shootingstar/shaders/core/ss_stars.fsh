#version 150

in vec4 vertexColor;
in vec2 corner;
in float streak;

out vec4 fragColor;

void main() {
    float across = exp(-corner.y * corner.y * 3.5);
    float along = streak > 0.0 ? (corner.x < 0.0 ? exp(-corner.x * corner.x * 3.5) : 1.0 - corner.x * 0.85)
        : exp(-corner.x * corner.x * 3.5);
    float a = across * along * vertexColor.a * (1.0 - streak * 0.6);
    fragColor = vec4(vertexColor.rgb * a, 1.0);
}
