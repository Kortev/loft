#version 150

// Flying chunks of terrain: block textures from the atlas, lit by the sky and the fireball,
// glowing while they are still hot (vertex alpha).

uniform sampler2D Sampler0;
uniform vec3 LightDir;
uniform vec3 SkyLight;
uniform vec3 FireLight;
uniform vec3 FirePos;
uniform float FireRange;

in vec2 texCoord;
in vec4 vertexColor;
in vec3 normal;
in vec3 worldPos;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord);
    if (tex.a < 0.1) {
        discard;
    }
    vec3 n = normalize(normal);
    float sun = max(dot(n, normalize(LightDir)), 0.0);
    vec3 toFire = FirePos - worldPos;
    float fd = length(toFire);
    float fire = max(dot(n, toFire / max(fd, 0.001)), 0.0) * clamp(1.0 - fd / FireRange, 0.0, 1.0);
    vec3 light = SkyLight * (0.55 + 0.45 * n.y) + vec3(1.0, 0.96, 0.9) * sun * 0.6 + FireLight * fire;
    vec3 color = tex.rgb * vertexColor.rgb * light;
    float heat = vertexColor.a;
    color += (tex.rgb * vec3(1.0, 0.5, 0.2) + vec3(1.0, 0.35, 0.05)) * heat * heat * 1.4;
    fragColor = vec4(color, 1.0);
}
