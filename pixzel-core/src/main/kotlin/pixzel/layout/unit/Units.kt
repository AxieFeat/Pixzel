package pixzel.layout.unit

interface SizeSpec
@JvmInline
value class Dp(val value: Float) : SizeSpec {

    init {
        require(value.isFinite())
    }
}

@JvmInline
value class Sp(val value: Float) {

    init {
        require(value.isFinite() && value >= 0)
    }
}

@JvmInline
value class Px(val value: Float) {

    init {
        require(value.isFinite())
    }
}

@JvmInline
value class Percent(val value: Float) : SizeSpec {

    init {
        require(value.isFinite() && value >= 0)
    }
}

data object FillAvailable : SizeSpec
data object WrapContent : SizeSpec

val fillAvailable: SizeSpec = FillAvailable
val wrapContent: SizeSpec = WrapContent
val Int.dp get() = Dp(toFloat())
val Float.dp get() = Dp(this)
val Double.dp get() = Dp(toFloat())
val Int.sp get() = Sp(toFloat())
val Float.sp get() = Sp(this)
val Int.px get() = Px(toFloat())
val Int.percent get() = Percent(toFloat() / 100)
val Float.percent get() = Percent(this / 100)
operator fun Dp.plus(other: Dp) = Dp(value + other.value)
operator fun Dp.minus(other: Dp) = Dp(value - other.value)
operator fun Dp.times(factor: Float) = Dp(value * factor)
