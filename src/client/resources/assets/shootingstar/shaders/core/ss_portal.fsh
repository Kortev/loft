#version 150

// The inside of the tear: whatever the camera saw of the other universe, pinned to the screen.

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb, 1.0);
}
