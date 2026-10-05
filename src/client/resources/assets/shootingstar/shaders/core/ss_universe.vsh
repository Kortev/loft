#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 objPos;
out vec3 objNormal;
out vec3 camObj;
out vec3 viewPos;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    viewPos = pos.xyz;
    objPos = Position;
    objNormal = Normal;
    // Where the eye is in the object's own space, so the fragment shader can march the inside.
    camObj = (inverse(ModelViewMat) * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
}
