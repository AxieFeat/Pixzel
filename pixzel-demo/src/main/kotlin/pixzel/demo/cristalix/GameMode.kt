package pixzel.demo.cristalix

internal data class GameMode(
    val id: String,
    val name: String,
    val online: Int,
    val color: Int,
    val icon: String,
    val category: String = MenuCategory.ALL,
    val badge: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val languages: Set<String> = setOf("RU", "US"),
    val reviewOrder: Int = Int.MAX_VALUE,
    val rating: GameRating = GameRating()
)

internal data class GameRating(
    val positive: Int = 0,
    val negative: Int = 0,
    val total: Int = positive + negative,
    val description: String = "Нет оценок",
    val tags: String = "",
    val playedMinutes: Int? = null,
    val featured: Boolean = false
)
