#version 330
#extension GL_ARB_separate_shader_objects : require

// NovaUtils soft glow: a wide gaussian instead of vanilla's 2-pixel box blur, so the outline turns
// into a halo that fades out smoothly, while staying bright right at the edge.

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Spread and Strength come from the ESP glow sliders (see PostPassMixin)
layout(std140) uniform GlowConfig {
    vec2 BlurDir;
    float Spread;
    float Strength;
};

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 sampleStep = BlurDir / InSize * Spread;
    const float R = 11.0;
    const float SIGMA = 4.2;
    vec4 sum = vec4(0.0);
    float weights = 0.0;
    for (float a = -R; a <= R; a += 1.0) {
        float w = exp(-(a * a) / (2.0 * SIGMA * SIGMA));
        vec4 s = texture(InSampler, texCoord + sampleStep * a);
        sum += vec4(s.rgb * s.a, s.a) * w;
        weights += w;
    }
    vec4 avg = sum / weights;
    vec3 color = avg.a > 0.0001 ? avg.rgb / avg.a : vec3(0.0);
    // soft curve: full strength at the edge, a long fade outwards
    fragColor = vec4(color, 1.0 - exp(-avg.a * 2.6 * Strength));
}
