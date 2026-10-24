package pixzel.state

/** Identity-keyed local state slot. Reuse the same typed token when updating a retained scope. */
class StateKey<T>(val name: String)
