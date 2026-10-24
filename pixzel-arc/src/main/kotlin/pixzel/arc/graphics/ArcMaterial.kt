package pixzel.arc.graphics

import arc.graphics.RenderContext
import arc.shader.ShaderInstance
import arc.texture.Texture
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector4f
import pixzel.graphics.Brush
import pixzel.graphics.Color
import pixzel.graphics.ColorStop
import pixzel.graphics.ImageBrush
import pixzel.graphics.LinearGradient
import pixzel.graphics.RadialGradient
import pixzel.graphics.Sampling
import pixzel.graphics.Solid
import pixzel.surface.Transform
import pixzel.surface.Size


/**
 * Maps independent brushes to shader uniforms; textures and the program remain session-owned.
 */
internal class ArcMaterial(
    private val context: RenderContext,
    private val shader: ShaderInstance,
    private val bindImage: (Texture, Sampling) -> Unit,
) {

    private val matrix = Matrix4f()
    private val vector = Vector2f()
    private val colorVector = Vector4f()

    fun prepare(brush: Brush, viewport: Size, transform: Transform) {
        context.bindShader(shader)
        shader.setUniform("viewportSize", vector.set(viewport.width, viewport.height))
        matrix.identity().m00(transform.a).m01(transform.b).m10(transform.c).m11(transform.d).m30(transform.tx).m31(transform.ty)
        shader.setUniform("uiTransform", matrix)
        shader.setUniform("blurStep", vector.set(0f, 0f))
        shader.setUniform("vertexColors", false)
        when (brush) {
            is Solid -> {
                shader.setUniform("paintMode", 0)
                setColor("baseColor", brush.color)
            }

            is LinearGradient -> {
                shader.setUniform("paintMode", 1)
                shader.setUniform("gradientFrom", vector.set(brush.from.x, brush.from.y))
                shader.setUniform("gradientTo", vector.set(brush.to.x, brush.to.y))
                stops(brush.stops)
            }

            is RadialGradient -> {
                shader.setUniform("paintMode", 2)
                shader.setUniform("gradientFrom", vector.set(brush.center.x, brush.center.y))
                shader.setUniform("gradientRadius", brush.radius)
                stops(brush.stops)
            }

            is ImageBrush -> {
                val image = brush.image as? ArcImageHandle ?: throw IllegalArgumentException("Image brush requires an Arc image")
                check(!image.isClosed) { "Image '${image.id}' is closed" }
                val inverse = brush.transform.inverse() ?: error("Image brush transform is singular")
                shader.setUniform("paintMode", 4)
                setColor("baseColor", Color.WHITE)
                shader.setUniform(
                    "brushMapping",
                    matrix.identity().m00(inverse.a).m01(inverse.b).m10(inverse.c).m11(inverse.d).m30(inverse.tx).m31(inverse.ty)
                )
                shader.setUniform("brushImageSize", vector.set(image.size.width, image.size.height))
                shader.setUniform(
                    "brushSource", colorVector.set(
                        brush.source.x / image.size.width, brush.source.y / image.size.height,
                        brush.source.width / image.size.width, brush.source.height / image.size.height
                    )
                )
                shader.setUniform("brushFlip", image.flipVertically)
                shader.setUniform("imagePremultiplied", image.premultiplied)
                shader.setUniform("imageTexture", 0)
                bindImage(image.resolve(context), brush.sampling)
            }

            else -> throw UnsupportedOperationException("Brush ${brush::class.qualifiedName} has no Arc material")
        }
    }

    private fun stops(stops: List<ColorStop>) {
        require(stops.size <= 64) { "Arc gradient supports up to 64 color stops" }
        require(stops.zipWithNext().all { it.first.offset <= it.second.offset }) { "Gradient stops must be ordered" }
        shader.setUniform("stopCount", stops.size)
        stops.forEachIndexed { index, stop ->
            shader.setUniform("stopPosition[$index]", stop.offset)
            setColor("stopColor[$index]", stop.color)
        }
    }

    fun setColor(name: String, color: Color, opacity: Float = 1f) {
        shader.setUniform(name, colorVector.set(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f * opacity))
    }

    companion object {

        val VERTEX = """
            #version 410 core
            layout(location = 0) in vec3 position;
            layout(location = 1) in vec2 coordinates;
            layout(location = 2) in vec4 color;
            uniform vec2 viewportSize;
            uniform mat4 uiTransform;
            out vec2 localPoint;
            out vec4 paintColor;

            void main() {
                vec4 p = uiTransform * vec4(position, 1);
                gl_Position = vec4(
                    p.x * 2 / viewportSize.x - 1,
                    1 - p.y * 2 / viewportSize.y,
                    0,
                    1
                );
                localPoint = coordinates;
                paintColor = color;
            }
        """.trimIndent()
        val FRAGMENT = """
            #version 410 core
            in vec2 localPoint;
            in vec4 paintColor;
            out vec4 result;
            uniform int paintMode;
            uniform vec4 baseColor;
            uniform bool vertexColors;
            uniform vec2 gradientFrom;
            uniform vec2 gradientTo;
            uniform float gradientRadius;
            uniform int stopCount;
            uniform float stopPosition[64];
            uniform vec4 stopColor[64];
            uniform sampler2D imageTexture;
            uniform bool imagePremultiplied;
            uniform vec2 blurStep;
            uniform mat4 brushMapping;
            uniform vec2 brushImageSize;
            uniform vec4 brushSource;
            uniform bool brushFlip;

            vec4 gradient(float t) {
                vec4 c = stopColor[0];

                for (int i = 1; i < stopCount; i++) {
                    if (t <= stopPosition[i]) {
                        float span = stopPosition[i] - stopPosition[i - 1];
                        return mix(
                            stopColor[i - 1],
                            stopColor[i],
                            span > 0 ? clamp((t - stopPosition[i - 1]) / span, 0, 1) : 1
                        );
                    }

                    c = stopColor[i];
                }

                return c;
            }

            void main() {
                if (paintMode == 3 || paintMode == 4) {
                    vec2 uv = localPoint;

                    if (paintMode == 4) {
                        vec2 q = (brushMapping * vec4(localPoint, 0, 1)).xy;
                        vec2 halfPixel = min(vec2(.5) / brushImageSize, brushSource.zw * .5);
                        uv = clamp(
                            brushSource.xy + q / brushImageSize,
                            brushSource.xy + halfPixel,
                            brushSource.xy + brushSource.zw - halfPixel
                        );

                        if (brushFlip)
                            uv.y = 1 - uv.y;
                    }

                    vec4 c = texture(imageTexture, uv);

                    if (dot(blurStep, blurStep) > 0) {
                        c *= 0.2270270270;
                        c += (texture(imageTexture, uv + blurStep) + texture(imageTexture, uv - blurStep)) * 0.1945945946;
                        c += (texture(imageTexture, uv + 2 * blurStep) + texture(imageTexture, uv - 2 * blurStep)) * 0.1216216216;
                        c += (texture(imageTexture, uv + 3 * blurStep) + texture(imageTexture, uv - 3 * blurStep)) * 0.0540540541;
                        c += (texture(imageTexture, uv + 4 * blurStep) + texture(imageTexture, uv - 4 * blurStep)) * 0.0162162162;
                    }

                    result = vec4(
                        (imagePremultiplied ? c.rgb : c.rgb * c.a) * baseColor.rgb * baseColor.a,
                        c.a * baseColor.a
                    );
                    return;
                }

                vec4 c = vertexColors ? paintColor : baseColor;

                if (paintMode == 1) {
                    vec2 d = gradientTo - gradientFrom;
                    float q = dot(d, d);
                    c = gradient(q > 0 ? clamp(dot(localPoint - gradientFrom, d) / q, 0, 1) : 0);
                }

                if (paintMode == 2)
                    c = gradient(clamp(length(localPoint - gradientFrom) / gradientRadius, 0, 1));

                result = vec4(c.rgb * c.a, c.a);
            }
        """.trimIndent()
    }
}
