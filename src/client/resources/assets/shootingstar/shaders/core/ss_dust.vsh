#version 150

// Billboarded puffs of Mjölnir's dust and steam. Normal carries per-puff data in signed bytes: x = seed.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 corner;
out vec4 vertexColor;
out float seed;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    corner = UV0;
    vertexColor = Color;
    seed = Normal.x;
}
