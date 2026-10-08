#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 vertexColor;
// Where the point is from the camera, for the fragment to measure: a distance worked out at the corners of a quad
// hundreds of blocks across and blended between them is nowhere near the distance in its middle.
out vec3 viewPos;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    uv = UV0;
    vertexColor = Color;
    viewPos = pos.xyz;
}
