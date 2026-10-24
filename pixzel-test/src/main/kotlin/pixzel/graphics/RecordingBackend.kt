package pixzel.graphics

import pixzel.surface.Rect


data class RecordedFrame(val sessionId: Long, val info: FrameInfo, val commands: List<DrawCommand>)

/** Records validated painter order and scoped operations without graphics/native dependencies. */
class RecordingBackend private constructor(override val capabilities: BackendCapabilities) : UiBackend {

    private var nextSession = 0L
    private val recorded = mutableListOf<RecordedFrame>()
    val frames: List<RecordedFrame> get() = recorded.toList()
    val lastFrame: RecordedFrame? get() = recorded.lastOrNull()
    var activeSessions: Int = 0
        private set

    override fun openSession(): RenderSession {
        val id = ++nextSession
        activeSessions++
        return object : RenderSession {
            private var closed = false
            override fun submit(frame: FrameInfo, commands: List<DrawCommand>) {
                check(!closed) { "Recording session is closed." }
                validateCommands(frame, commands, capabilities)
                recorded += RecordedFrame(id, frame, commands.toList())
            }

            override fun close() {
                if (!closed) {
                    closed = true
                    activeSessions--
                }
            }
        }
    }

    fun clear() = recorded.clear()

    companion object {

        fun of(capabilities: BackendCapabilities = BackendCapabilities(Capability.entries.toSet(), "Recording backend")) =
            RecordingBackend(capabilities)
    }
}

internal fun validateCommands(frame: FrameInfo, commands: List<DrawCommand>, capabilities: BackendCapabilities) {
    require(frame.pixelsPerDp.x > 0f && frame.pixelsPerDp.y > 0f) { "Framebuffer scale must be positive." }
    val stack = ArrayDeque<String>()
    fun shape(shape: Shape) {
        if (shape is VectorPath) capabilities.require(Capability.PATH)
    }

    fun image(image: ImageHandle, source: Rect) {
        capabilities.require(Capability.IMAGE)
        check(!image.isClosed) { "Image '${image.id}' is closed." }
        require(source.x >= 0 && source.y >= 0 && source.right <= image.size.width && source.bottom <= image.size.height) { "Image source is outside '${image.id}'." }
    }

    fun brush(brush: Brush) {
        when (brush) {
            is Solid -> Unit
            is LinearGradient, is RadialGradient -> capabilities.require(Capability.GRADIENT)
            is ImageBrush -> image(brush.image, brush.source)
            else -> capabilities.require(Capability.CUSTOM)
        }
    }
    commands.forEachIndexed { index, command ->
        when (command) {
            Save -> stack += "save"
            Restore -> check(stack.removeLastOrNull() == "save") { "Unbalanced Restore at command $index." }
            is Concat -> Unit
            is Clip -> {
                capabilities.require(if (command.shape === Rectangle) Capability.RECT_CLIP else Capability.SHAPE_CLIP)
                shape(command.shape)
            }

            is Fill -> {
                shape(command.shape)
                brush(command.brush)
            }

            is Stroke -> {
                require(command.width.isFinite() && command.width >= 0f) { "Stroke width must be finite and nonnegative." }
                shape(command.shape)
                brush(command.brush)
            }

            is DrawText -> capabilities.require(Capability.TEXT)
            is DrawImage -> image(command.image, command.source)
            is BeginGroup -> {
                capabilities.require(Capability.GROUP_OPACITY)
                stack += "group"
            }

            EndGroup -> check(stack.removeLastOrNull() == "group") { "Unbalanced EndGroup at command $index." }
            is Shadow -> {
                capabilities.require(Capability.SHADOW)
                require(command.blur.isFinite() && command.blur >= 0f && command.spread.isFinite()) { "Invalid shadow blur/spread." }
                shape(command.shape)
            }

            is CustomCommand -> {
                capabilities.require(Capability.CUSTOM)
                command.requiredCapabilities.forEach(capabilities::require)
            }

            else -> capabilities.require(Capability.CUSTOM)
        }
    }
    check(stack.isEmpty()) { "Unclosed render scopes: ${stack.joinToString()}." }
}
