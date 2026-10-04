#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Offset;

out vec3 localPos;
out float worldY;
out vec4 vertexColor;
out vec2 faceUV;

void main() {
    vec3 rel = Position + Offset;
    gl_Position = ProjMat * ModelViewMat * vec4(rel, 1.0);
    localPos = Position;
    worldY = rel.y;
    vertexColor = Color;
    faceUV = UV0;
}
