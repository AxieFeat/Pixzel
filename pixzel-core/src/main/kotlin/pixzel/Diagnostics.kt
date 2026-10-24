package pixzel

enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}
data class Diagnostic(
    val code: String,
    val message: String,
    val elementPath: String = "",
    val severity: DiagnosticSeverity = DiagnosticSeverity.WARNING
)

/**
 * Bounded, deduplicated UI-owned diagnostic history; it never prints per-frame spam.
 */
class DiagnosticLog(private val capacity: Int = 256) {

    private val entries = linkedMapOf<Pair<String, String>, Diagnostic>()

    init {
        require(capacity > 0)
    }

    val messages: List<Diagnostic> get() = entries.values.toList()
    fun report(diagnostic: Diagnostic) {
        val key = diagnostic.code to diagnostic.elementPath
        entries[key] = diagnostic
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    fun clear() {
        entries.clear()
    }
}

enum class ErrorDecision {
    THROW,
    CONTINUE
}
data class UiFailure(val error: Throwable, val phase: String, val elementPath: String)
fun interface UiErrorPolicy {

    fun handle(failure: UiFailure): ErrorDecision
}
