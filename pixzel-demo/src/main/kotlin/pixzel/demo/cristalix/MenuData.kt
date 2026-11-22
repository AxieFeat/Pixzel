@file:Suppress("StringLiteralDuplication") // TSV column names are repeated to keep each schema explicit.

package pixzel.demo.cristalix

/**
 * Content supplied to the scene; screen builders have no catalogue-specific branches.
 */
internal data class MenuData(
    val games: List<GameMode>,
    val banners: List<Promotion>,
    val reviews: List<PlayerReview>,
    val metrics: ReviewMetrics = ReviewMetrics(
        games.size, reviews.size,
        reviews.count { it.positive }, reviews.count { !it.positive }),
    val languages: List<String> = games.flatMap { it.languages }.distinct()
) {

    init {
        require(games.map { it.id }.distinct().size == games.size) { "Game IDs must be unique" }
        require(banners.isNotEmpty()) { "At least one promotion is required" }
    }

    companion object {

        fun load(): MenuData {
            val metrics = rows("metrics").single()
            return MenuData(
                rows("games").map(::game), rows("banners").map(::promotion), rows("reviews").map(::review),
                ReviewMetrics(metrics.int("modes"), metrics.int("reviews"), metrics.int("positive"), metrics.int("negative")),
                languages = rows("languages").map { it.getValue("code") }
            )
        }

        private fun game(row: Map<String, String>) = GameMode(
            id = row.getValue("id"), name = row.getValue("name"), online = row.int("online"),
            color = row.getValue("color").toInt(16), icon = row.getValue("icon"), category = row.getValue("category"),
            badge = row.getValue("badge"), description = row.getValue("description"),
            tags = row.getValue("tags").split('|').filter(String::isNotEmpty),
            languages = row.getValue("languages").split('|').toSet(), reviewOrder = row.int("reviewOrder"),
            rating = GameRating(
                row.int("positive"), row.int("negative"), row.int("reviews"),
                row.getValue("rating"), row.getValue("ratingTags"), row.getValue("playedMinutes").toIntOrNull(),
                row.getValue("featured").toBooleanStrict()
            )
        )

        private fun promotion(row: Map<String, String>) = Promotion(
            row.getValue("image"), row.getValue("title"),
            row.getValue("action"), row.float("duration"), row.float("buttonWidth")
        )

        private fun review(row: Map<String, String>) = PlayerReview(
            row.getValue("author"), row.getValue("hours"),
            row.getValue("date"), row.getValue("comment"), row.getValue("positive").toBooleanStrict(),
            row.int("span"), row.float("height"), row.float("expandedHeight")
        )

        private fun rows(name: String): List<Map<String, String>> =
            checkNotNull(MenuData::class.java.getResourceAsStream("/cristalix/data/$name.tsv")) { "Missing menu data: $name" }
                .bufferedReader(Charsets.UTF_8)
                .use { reader ->
                    val columns = reader.readLine().split('\t')
                    reader.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
                        val values = line.split('\t').map(::unescape)
                        require(values.size == columns.size) { "Invalid column count in $name: ${values.firstOrNull()}" }
                        columns.zip(values).toMap()
                    }.toList()
                }

        private fun Map<String, String>.int(key: String) = getValue(key).toInt()
        private fun Map<String, String>.float(key: String) = getValue(key).toFloat()

        private fun unescape(value: String): String = buildString {
            var escaped = false
            for (character in value) {
                if (escaped) {
                    append(
                        when (character) {
                            'n' -> '\n'; 't' -> '\t'; else -> character
                        }
                    )
                    escaped = false
                } else if (character == '\\') escaped = true else append(character)
            }
            if (escaped) append('\\')
        }
    }
}

internal data class Promotion(val image: String, val title: String, val action: String, val seconds: Float, val buttonWidth: Float) {
    init {
        require(seconds > 0f && buttonWidth > 0f)
    }
}

internal data class ReviewMetrics(val modes: Int, val reviews: Int, val positive: Int, val negative: Int) {

    val total get() = positive + negative
    val approval get() = if (total == 0) 0 else kotlin.math.round(positive * 100f / total).toInt()
}
