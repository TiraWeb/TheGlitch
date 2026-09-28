#version 330
// The Glitch: MythicHUD + NMinimap merged core/text vertex shader (26.2).
// Both plugins replace this one shader. MythicHUD only rewrites GUI text
// (IS_GUI: the top-left HUD); NMinimap only rewrites world text (its map item
// frame + markers). So one shader can host both. NMinimap's own copies are
// switched off (NMinimap config: resourcepack.pack-mcmeta.overlays.*: false);
// its includes (nminimap:*.glsl) still ship in its pack.
#define UNREL_ID

#define MAP_DEPTH 1.0
#define MARKER_DEPTH 1.0

#ifdef GL_ARB_shader_draw_parameters
#extension GL_ARB_shader_draw_parameters : require
#endif

#moj_import <nminimap:config.glsl>

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#endif

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform sampler2D Sampler0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
#endif

out vec4 vertexColor;
out vec2 texCoord0;

flat out int custom;
out vec2 uvCoord;

#if defined(IS_GUI)
#define MH_VERSION 5
#moj_import <minecraft:mythichud_utils.glsl>
#endif

#moj_import <nminimap:vertex_utils.glsl>

void main() {
    custom = 0;
    uvCoord = vec2(0);

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);

    #moj_import <nminimap:vertex_body.glsl>
#else
    vertexColor = Color;
#endif

#if defined(IS_GUI)
    applyCustomHud();
#endif
}
