#version 150

// The edge of the world the game has loaded round the player, seen from a camera shot far from them: the ground there
// fades into the murk of the storm (Murk, the colour the void past it shows) over the last stretch before it ends,
// instead of stopping in a hard square. Sampler0 is the picture, Sampler1 its depth; InvProj and InvView take a pixel
// back to the world (relative to the camera), Player is where the player is from the camera, Radius how far round
// them the world is loaded.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 InvProj;
uniform mat4 InvView;
uniform vec3 Player;
uniform float Radius;
uniform vec3 Murk;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord);
    float depth = texture(Sampler1, texCoord).r;
    if (depth >= 1.0) {
        fragColor = color;
        return;
    }
    vec4 view = InvProj * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 world = (InvView * vec4(view.xyz / view.w, 0.0)).xyz;
    float far = length((world - Player).xz);
    float fade = smoothstep(Radius * 0.7, Radius * 0.93, far);
    fragColor = vec4(mix(color.rgb, Murk, fade), color.a);
}
