#version 330
#extension GL_ARB_separate_shader_objects : require

// NovaUtils soft glow: the edge finder looks 2 pixels out, so the outline it hands to the blur is
// thicker and the glow reads at any distance.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main(){
    vec2 oneTexel = 2.0 / InSize;

    vec4 center = texture(InSampler, texCoord);
    vec4 left = texture(InSampler, texCoord - vec2(oneTexel.x, 0.0));
    vec4 right = texture(InSampler, texCoord + vec2(oneTexel.x, 0.0));
    vec4 up = texture(InSampler, texCoord - vec2(0.0, oneTexel.y));
    vec4 down = texture(InSampler, texCoord + vec2(0.0, oneTexel.y));
    float total = clamp(abs(center.a - left.a) + abs(center.a - right.a) + abs(center.a - up.a) + abs(center.a - down.a), 0.0, 1.0);
    float cover = center.a + left.a + right.a + up.a + down.a;
    vec3 outColor = (center.rgb * center.a + left.rgb * left.a + right.rgb * right.a + up.rgb * up.a + down.rgb * down.a) / max(cover, 0.0001);
    fragColor = vec4(outColor, total);
}
