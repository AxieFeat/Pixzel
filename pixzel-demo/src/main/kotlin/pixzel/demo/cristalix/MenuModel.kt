package pixzel.demo.cristalix

import java.nio.file.Files
import java.nio.file.Path

internal object MenuCategory {

    const val ALL = "Все игры"
    const val FAVORITES = "Избранное"
    const val HUBS = "Хабы"
    const val TESTING = "Тестовые"
    const val ARCADE = "Аркады"
    const val TABLETOP = "Настолки"
}

internal class MenuModel(private val favoritesFile: Path? = null, val data: MenuData = MenuData.load()) {

    val favorites = linkedSetOf<String>()
    var query = ""
    var category = MenuCategory.ALL
    var language = "RU"
    var reducedMotion = false
    var revision = 0
        private set

    init {
        if (favoritesFile != null && Files.isRegularFile(favoritesFile)) {
            favorites += Files.readAllLines(favoritesFile).filter { id -> data.games.any { it.id == id } }
        }
    }

    fun visibleModes(): List<GameMode> = data.games.filter { game ->
        val inCategory = when (category) {
            MenuCategory.ALL -> game.category != MenuCategory.TESTING && game.category != MenuCategory.HUBS
            MenuCategory.FAVORITES -> game.id in favorites
            else -> game.category == category
        }
        val inLanguage = language in game.languages
        inCategory && inLanguage &&
                game.name.replace('\n', ' ').contains(query.trim(), ignoreCase = true)
    }

    fun toggleFavorite(id: String) {
        require(data.games.any { it.id == id })
        if (!favorites.add(id)) favorites.remove(id)
        revision++

        favoritesFile?.let { path ->
            Files.createDirectories(path.toAbsolutePath().parent)
            Files.write(path, favorites)
        }
    }

}
