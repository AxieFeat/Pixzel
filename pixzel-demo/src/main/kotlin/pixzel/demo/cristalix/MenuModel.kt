package pixzel.demo.cristalix

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.exp

internal object MenuCategory {

    const val ALL = "Все игры"
    const val FAVORITES = "Избранное"
    const val HUBS = "Хабы"
    const val TESTING = "Тестовые"
    const val ARCADE = "Аркады"
    const val TABLETOP = "Настолки"
}

internal data class GameMode(
    val id: String,
    val name: String,
    val online: Int,
    val color: Int,
    val icon: String,
    val category: String = MenuCategory.ALL,
    val badge: String = "",
    val description: String = "Собери друзей, выбери свою тактику и отправляйся навстречу приключениям. Каждая игра — новая история!"
)

@Suppress("MagicNumber")
internal val gameModes = listOf(
    GameMode("prison", "PrisonEvo", 74, 0x548C31, "pickaxe", badge = "ВАЙП"),
    GameMode(
        "wool", "WoolWars", 9, 0x548C31, "duel", MenuCategory.ARCADE, "РЕЛИЗ",
        "Выбирай любимый класс и его уникальные навыки. Сражайся за точку в центре! Побеждает команда, набравшая 3 очка за захват."
    ),
    GameMode("swords", "Мастера\nМечей", 454, 0x102D4D, "trophy"),
    GameMode("chaos", "Custom\nSteveChaos", 277, 0xC96C43, "sword"),
    GameMode("skyblock", "SkyBlock Old", 140, 0x28282D, "crate"),
    GameMode("azerus", "Azerus", 61, 0x6C090B, "book"),
    GameMode("space", "SpaceTycoon", 60, 0x1B3D7D, "server"),
    GameMode("survival", "Выживание\nБомжа", 59, 0x1B3D7D, "box"),
    GameMode("castle", "CastleFight", 45, 0x76090B, "tower", MenuCategory.ARCADE),
    GameMode("arcade", MenuCategory.ARCADE, 40, 0x292063, "controller", MenuCategory.ARCADE),
    GameMode("oldschool", "Prison\nOldSchool", 32, 0x6650D9, "bars"),
    GameMode("classic", "Prison Classic", 28, 0xD98200, "cuffs"),
    GameMode("computer", "Компьютерная\nИмперия", 26, 0x17114D, "computer"),
    GameMode("anarchy", "Анархия", 26, 0x790A0C, "wither"),
    GameMode("trigger", "TriggerRush", 22, 0x1B3D7D, "rifle", MenuCategory.TESTING, "ПРОТОТИП"),
    GameMode("bingo", "Бинго", 21, 0x292063, "bingo", MenuCategory.TABLETOP),
    GameMode("slime", "Slime RNG", 18, 0x274965, "slime"),
    GameMode("food", "Foodcourt", 17, 0x17496C, "burger"),
    GameMode("bedwars", "BedWars", 103, 0x1B3D7D, "bed", MenuCategory.ARCADE),
    GameMode("skywars", "SkyWars", 88, 0x548C31, "island", MenuCategory.ARCADE),
    GameMode("parkour", "Паркур", 34, 0x4696AB, "boots", MenuCategory.ARCADE),
    GameMode("creative", "Творческий", 12, 0x7956AC, "pickaxe", MenuCategory.TESTING),
    GameMode("chess", "Шахматы", 16, 0x292063, "tower", MenuCategory.TABLETOP),
    GameMode("hub", "Главный хаб", 148, 0xC49800, "compass", MenuCategory.HUBS)
)

internal class MenuModel(private val favoritesFile: Path? = null) {

    val favorites = linkedSetOf<String>()
    var query = ""
    var category = MenuCategory.ALL
    var english = false
    var reducedMotion = false
    var revision = 0
        private set

    init {
        if (favoritesFile != null && Files.isRegularFile(favoritesFile)) {
            favorites += Files.readAllLines(favoritesFile).filter { id -> gameModes.any { it.id == id } }
        }
    }

    fun visibleModes(): List<GameMode> = gameModes.filter { game ->
        val inCategory = when (category) {
            MenuCategory.ALL -> true
            MenuCategory.FAVORITES -> game.id in favorites
            else -> game.category == category
        }
        inCategory && game.name.replace('\n', ' ').contains(query.trim(), ignoreCase = true)
    }

    fun toggleFavorite(id: String) {
        require(gameModes.any { it.id == id })
        if (!favorites.add(id)) favorites.remove(id)
        revision++

        favoritesFile?.let { path ->
            Files.createDirectories(path.toAbsolutePath().parent)
            Files.write(path, favorites)
        }
    }

    fun label(russian: String, english: String) = if (this.english) english else russian
}

/**
 * Exact critically damped motion stays stable when frames arrive at different intervals.
 */
internal class MenuSpring(var value: Float = 0f, private val frequency: Float = 16f) {

    var target = value
    private var velocity = 0f

    fun step(seconds: Float): Boolean {
        if (abs(value - target) < SETTLE_EPSILON && abs(velocity) < SETTLE_EPSILON) {
            value = target
            velocity = 0f
            return false
        }

        val dt = seconds.coerceIn(0f, MAXIMUM_STEP)
        val offset = value - target
        // Solve the critically damped spring analytically instead of accumulating Euler integration error.
        // The exponential term makes small and large frame intervals converge to the same trajectory.
        val combined = velocity + frequency * offset
        val decay = exp(-frequency * dt)
        value = target + (offset + combined * dt) * decay
        velocity = (velocity - frequency * combined * dt) * decay
        return true
    }

    fun snap(target: Float = this.target) {
        this.target = target
        value = target
        velocity = 0f
    }

    companion object {

        private const val SETTLE_EPSILON = 0.001f
        private const val MAXIMUM_STEP = 0.05f
    }
}
