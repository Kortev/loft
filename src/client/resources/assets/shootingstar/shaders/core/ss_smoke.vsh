#version 150

// Billboarded puffs of dust, smoke and fire. Normal carries per-puff data: x = seed, y = spin,
// z = fire glow (emitted light on top of the lit smoke).

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 corner;
out vec2 local;
out vec4 vertexColor;
out float seed;
out float glow;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    float a = Normal.y * 3.14159265;
    corner = mat2(cos(a), -sin(a), sin(a), cos(a)) * UV0;
    local = UV0;
    vertexColor = Color;
    seed = Normal.x;
    glow = Normal.z;
}
