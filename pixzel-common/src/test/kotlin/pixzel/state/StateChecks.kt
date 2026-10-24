package pixzel.state

import org.junit.jupiter.api.Test

/**
 * Executable without a test framework, and callable by the integrated contract-check suite.
 */
fun runStateChecks() {
    val runtime = stateRuntime()
    val first = stateOf(1, runtime, "first")
    val second = stateOf(2, runtime, "second")
    var evaluations = 0
    val sum = bind(runtime, "sum") {
        evaluations++
        first.value + second.value
    }
    val double = derivedStateOf(runtime, "double") { sum.value * 2 }
    val observed = mutableListOf<List<Int>>()
    val sumListener = sum.observe { observed += listOf(it, first.value, second.value, double.value) }
    transaction(runtime) {
        first.value = 10
        transaction(runtime) { second.value = 20 }
        check(observed.isEmpty())
    }
    check(observed == listOf(listOf(30, 10, 20, 60))) { "Batch observers saw inconsistent values: $observed" }
    check(evaluations == 2) { "Batch must recompute a dependent expression once." }
    second.value = 20
    check(evaluations == 2)

    val choice = stateOf(true, runtime)
    var selectedEvaluations = 0
    val selected = bind(runtime) {
        selectedEvaluations++
        if (choice.value) first.value else second.value
    }
    check(selected.value == 10)
    choice.value = false
    val afterSwitch = selectedEvaluations
    first.value = 11
    check(selectedEvaluations == afterSwitch) { "Conditional dependencies were not removed." }
    second.value = 21
    check(selected.value == 21 && selectedEvaluations == afterSwitch + 1)

    val equality = stateOf(listOf(1, 2), runtime)
    var equalityCalls = 0
    val equalityListener = equality.observe { equalityCalls++ }
    equality.value = listOf(1, 2)
    check(equalityCalls == 0)
    equality.value = listOf(1, 2, 3)
    check(equalityCalls == 1)

    var delegated by stateOf(3, runtime)
    delegated += 4
    check(delegated == 7)
    val delegatedDerived by derivedStateOf(runtime) { delegated * 2 }
    check(delegatedDerived == 14)

    val stable = first.value
    var firstCalls = 0
    val firstListener = first.observe { firstCalls++ }
    transaction(runtime) {
        first.value = 80
        first.value = stable
    }
    check(firstCalls == 0) { "A batch returning to its old snapshot should not notify." }

    val bodyFailure = IllegalArgumentException("transaction body")
    val throwingListener = first.observe { throw IllegalStateException("observer") }
    val propagated = runCatching {
        transaction(runtime) {
            first.value = 99
            throw bodyFailure
        }
    }.exceptionOrNull()
    check(propagated === bodyFailure && bodyFailure.suppressed.size == 1)
    check(sum.value == 120) { "Exceptional transactions must finish derivations." }
    throwingListener.close()
    first.value = 100
    check(sum.value == 121) { "Exceptional transaction left the scheduler blocked." }

    val divisor = stateOf(1, runtime, "divisor")
    val divided = bind(runtime, "divided") { 10 / divisor.value }
    check(runCatching { divisor.value = 0 }.isFailure)
    check(runCatching { divided.value }.isFailure)
    divisor.value = 2
    check(divided.value == 5) { "A failed expression did not recover when its dependency changed." }

    val chooseCycle = stateOf(false, runtime)
    lateinit var cycleB: Binding<Int>
    val cycleA = bind(runtime, "cycleA") { if (chooseCycle.value) cycleB.value else 1 }
    cycleB = bind(runtime, "cycleB") { cycleA.value + 1 }
    val cycleError = runCatching { chooseCycle.value = true }.exceptionOrNull()
    check(cycleError?.message?.contains("cycleA -> cycleB -> cycleA") == true) { "Missing dependency-chain diagnostic: $cycleError" }
    chooseCycle.value = false
    check(cycleA.value == 1 && cycleB.value == 2)

    var closedCalls = 0
    val closeValue = selected.value
    selected.observe { closedCalls++ }
    selected.close()
    selected.close()
    second.value++
    check(selected.isClosed && selected.value == closeValue && closedCalls == 0)
    check(runCatching { selected.observe {} }.isFailure)

    var threadError: Throwable? = null
    val worker = Thread { threadError = runCatching { first.value++ }.exceptionOrNull() }
    worker.start()
    worker.join()
    check(threadError is IllegalStateException)
    check(runCatching {
        bind(runtime) {
            first.value++
            first.value
        }
    }.isFailure)

    val otherRuntime = stateRuntime()
    val foreign = stateOf(5, otherRuntime)
    check(runCatching { bind(runtime) { foreign.value } }.exceptionOrNull()?.message?.contains("same StateRuntime") == true)
    check(runCatching {
        bind(runtime) {
            foreign.value = 6
            first.value
        }
    }.isFailure)

    val feedbackSource = stateOf(0, runtime)
    val feedbackDerived = bind(runtime) { feedbackSource.value * 2 }
    var recovered = 0
    feedbackDerived.observe { recovered = it }
    val feedback = feedbackSource.observe { feedbackSource.value++ }
    check(runCatching { feedbackSource.value = 1 }.exceptionOrNull()?.message?.contains("notification cycle") == true)
    feedback.close()
    feedbackSource.value = 2_000
    check(recovered == 4_000) { "Derived observation was lost after feedback-cycle diagnosis." }
    feedbackDerived.close()

    // Owner cancellation must remove listeners repeatedly, without relying on frame polling.
    val life = stateOf(0, runtime)
    var staleCalls = 0
    repeat(1_000) {
        val owned = bind(runtime) { life.value * 3 }
        owned.observe { staleCalls++ }
        owned.close()
    }
    life.value++
    check(staleCalls == 0)

    listOf(sumListener, equalityListener, firstListener).forEach {
        it.close()
        it.close()
    }
    listOf(sum, double, divided, cycleA, cycleB).forEach { it.close() }
}

class StateRuntimeTest {

    @Test
    fun graphAndLifetime() = runStateChecks()
}

fun main() {
    runStateChecks()
    println("State checks passed")
}
