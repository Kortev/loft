#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 viewPos;
out vec3 viewNormal;
out vec2 texCoord;
out vec3 objPos;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    viewPos = pos.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    texCoord = UV0;
    objPos = Position;
}
