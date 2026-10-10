#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 viewPos;
out vec3 viewNormal;
out vec3 objPos;
out vec3 objNormal;
out vec4 vertexColor;
out vec2 texCoord;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    viewPos = pos.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    objPos = Position;
    objNormal = Normal;
    vertexColor = Color;
    texCoord = UV0;
}
