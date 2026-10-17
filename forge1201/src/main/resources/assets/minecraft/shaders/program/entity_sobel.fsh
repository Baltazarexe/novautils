#version 150

// NovaUtils soft glow: the edge finder looks 2 pixels out, so the outline is thicker.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main(){
    vec2 step2 = oneTexel * 2.0;
    vec4 center = texture(DiffuseSampler, texCoord);
    vec4 left = texture(DiffuseSampler, texCoord - vec2(step2.x, 0.0));
    vec4 right = texture(DiffuseSampler, texCoord + vec2(step2.x, 0.0));
    vec4 up = texture(DiffuseSampler, texCoord - vec2(0.0, step2.y));
    vec4 down = texture(DiffuseSampler, texCoord + vec2(0.0, step2.y));
    float total = clamp(abs(center.a - left.a) + abs(center.a - right.a) + abs(center.a - up.a) + abs(center.a - down.a), 0.0, 1.0);
    float cover = center.a + left.a + right.a + up.a + down.a;
    vec3 outColor = (center.rgb * center.a + left.rgb * left.a + right.rgb * right.a + up.rgb * up.a + down.rgb * down.a) / max(cover, 0.0001);
    fragColor = vec4(outColor, total);
}
