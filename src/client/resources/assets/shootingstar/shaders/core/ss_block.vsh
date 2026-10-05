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

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    objPos = Position;
    objNormal = Normal;
    // Where the eye is in the object's own space.
    camObj = (inverse(ModelViewMat) * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
}
