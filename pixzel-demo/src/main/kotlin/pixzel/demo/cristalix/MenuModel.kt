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

@Suppress("MagicNumber", "StringLiteralDuplication")
internal val gameModes = listOf(
    GameMode(
        "prison", "PrisonEvo", 119, 0x527F2C, "pickaxe", badge = "ВАЙП",
        description = "Выбери свой путь заключённого! Копай ресурсы, развивайся, открывай новые шахты и " +
                "сражайся с боссами. Стань самым сильным игроком тюрьмы!"
    ),
    GameMode(
        "wool", "WoolWars", 16, 0x527F2C, "duel", MenuCategory.ARCADE, badge = "РЕЛИЗ",
        description = "Выбирай любимый класс и его уникальные навыки. Сражайся за точку в центре! Побеждает команда, набравшая 3 очка за захват."
    ),
    GameMode("chaos", "Custom\nSteveChaos", 706, 0xD06C39, "sword"),
    GameMode("swords", "Мастера\nМечей", 452, 0x0C2442, "master"),
    GameMode("arcade", "Аркады", 333, 0x25205D, "controller", MenuCategory.ARCADE),
    GameMode(
        "skyblock", "SkyBlock Old", 178, 0x272726, "crate",
        description = "Начните с небольшого острова и превратите его в настоящую империю. " +
                "Добывайте ресурсы с генераторов, открывайте новые возможности для развития, " +
                "побеждайте дракона края и поднимитесь в топ лучших игроков сервера."
    ),
    GameMode(
        "castle", "CastleFight", 165, 0x700D00, "tower",
        description = "Сражайся в командных поединках! Строй замки и башни, призывай воинов и сокруши вражескую крепость. Победит лучшая стратегия!"
    ),
    GameMode("gta", "GTM - ГТА", 105, 0x0C2442, "pistol"),
    GameMode("azerus", "Azerus", 98, 0x700D00, "book"),
    GameMode("bingo", "Бинго", 94, 0x25205D, "bingo"),
    GameMode("trigger", "TriggerRush", 73, 0x133778, "rifle", badge = "ПРОТОТИП"),
    GameMode("survival", "Выживание\nБомжа", 72, 0x133778, "box"),
    GameMode("space", "SpaceTycoon", 54, 0x133778, "server"),
    GameMode("oldschool", "Prison\nOldSchool", 46, 0x6053DE, "bars"),
    GameMode("io", "IoGames", 41, 0x133778, "pacman"),
    GameMode("computer", "Компьютерная\nИмперия", 39, 0x120E42, "computer"),
    GameMode("classic", "Prison Classic", 38, 0xDF7D00, "cuffs"),
    GameMode("lucky", "LuckyWars", 36, 0x25205D, "lucky", MenuCategory.ARCADE),
    GameMode("words", "Слова", 34, 0x063535, "words", MenuCategory.ARCADE),
    GameMode("slime", "Slime RNG", 28, 0x213E61, "slime"),
    GameMode("tabletop", "Настолки", 25, 0x0C2442, "tabletop", MenuCategory.TABLETOP),
    GameMode("food", "Foodcourt", 24, 0x06426A, "burger"),
    GameMode("random", "RandomRush", 24, 0x408BB0, "random", MenuCategory.ARCADE),
    GameMode("ladder", "Лесенка", 22, 0xDF8C00, "ladder"),
    GameMode("creative", "Креатив", 20, 0x6A64BC, "lobby"),
    GameMode("sims", "Sims", 18, 0x00442C, "leaf"),
    GameMode("build", "Битва\nстроителей", 18, 0x063535, "build"),
    GameMode("columns", "Столбы", 17, 0x25205D, "columns", MenuCategory.ARCADE),
    GameMode("parade", "Парад Аркад", 16, 0x3676B4, "arcade-machine", MenuCategory.ARCADE),
    GameMode("anarchy", "Анархия", 14, 0x700D00, "wither"),
    GameMode("castles", "Castles", 12, 0x0C2442, "castles"),
    GameMode("jedigen", "JediCraft\nNewGen", 10, 0x25205D, "jedi-gen"),
    GameMode("farm", "MyLittleFarm", 9, 0x527F2C, "farm"),
    GameMode("woolbattle", "WoolBattle", 8, 0x700D00, "woolbattle", MenuCategory.ARCADE),
    GameMode("testing", "Тестовые", 8, 0x0C2442, "lobby"),
    GameMode("bedrock", "Бедроковая\nКоробка", 8, 0x272726, "bedrock", MenuCategory.ARCADE),
    GameMode("dungeons", "Dungeons", 7, 0x272726, "dungeon"),
    GameMode("chess", "Шахматы", 6, 0x25205D, "chess", MenuCategory.TABLETOP),
    GameMode("spread", "Spread", 6, 0x7560CD, "spread", MenuCategory.ARCADE),
    GameMode("deathrun", "DeathRun", 6, 0x133778, "skull", MenuCategory.ARCADE),
    GameMode("skycontrol", "SkyControl", 6, 0x162655, "skycontrol", MenuCategory.ARCADE),
    GameMode("colonists", "Колонисты", 5, 0x121D79, "colonists", MenuCategory.TABLETOP),
    GameMode("katana", "Симулятор\nКатаны", 5, 0x003D31, "katana"),
    GameMode("monopoly", "Монополия", 4, 0x25205D, "monopoly", MenuCategory.TABLETOP),
    GameMode("mind", "Игры Разума", 4, 0x25205D, "mind", MenuCategory.ARCADE),
    GameMode("bedwars", "BedWars", 4, 0x700D00, "bed", MenuCategory.ARCADE),
    GameMode("blockparty", "BlockParty", 4, 0xDF8C00, "record", MenuCategory.ARCADE),
    GameMode("last", "Last Survivor", 3, 0x700D00, "bat"),
    GameMode("spleef", "Spleef", 3, 0x408BB0, "spleef", MenuCategory.ARCADE),
    GameMode("jedi", "JediCraft New", 3, 0x133778, "jedi"),
    GameMode("garden", "Grow A\nGarden", 2, 0x00442C, "garden"),
    GameMode("bridge", "TheBridge", 2, 0x25205D, "bridge", MenuCategory.ARCADE),
    GameMode("events", "Ивенты", 2, 0xDF8C00, "trophy"),
    GameMode("bee", "BeeSwarm", 1, 0x133778, "bee"),
    GameMode("zombie", "ЗомбиМод", 1, 0x700D00, "zombie"),
    GameMode("treasure", "Симулятор\nСокровищ", 0, 0x251B51, "treasure"),
    GameMode("battleship", "Морской Бой", 0, 0x700D00, "battleship", MenuCategory.TABLETOP),
    GameMode("backgammon", "Нарды", 0, 0x063535, "backgammon", MenuCategory.TABLETOP),
    GameMode("checkers", "Шашки", 0, 0x133778, "checkers", MenuCategory.TABLETOP),
    GameMode("uno", "UNO", 0, 0x25205D, "uno", MenuCategory.TABLETOP),
    GameMode("lumber", "Симулятор\nЛесоруба", 0, 0xDF9800, "axe"),
    GameMode("ant", "AntWars", 0, 0x00442C, "ant", MenuCategory.ARCADE),
    GameMode("tnttag", "TntTag", 0, 0x0C2442, "dynamite", MenuCategory.ARCADE),
    GameMode("flood", "FloodEscape", 0, 0x133778, "flood", MenuCategory.ARCADE),
    GameMode("murder", "Murder\nMystery", 0, 0x700D00, "murder", MenuCategory.ARCADE),
    GameMode("hide", "HideAndSeek", 0, 0x133778, "hide", MenuCategory.ARCADE),
    GameMode(
        "cube", "Куб Смерти", 0, 0x25205D, "deathcube", MenuCategory.ARCADE,
        description = "Перемещайся по комнатам, подбирай ресурсы и бонусы, сражайся с другими игроками и " +
                "стань последним выжившим в смертельной битве!"
    ),
    GameMode("tom", "Том и Джерри", 0, 0x004A75, "tom", MenuCategory.ARCADE),
    GameMode("tntrun", "TntRun", 0, 0xDF7D00, "tnt", MenuCategory.ARCADE),
    GameMode(
        "bowspleef", "BowSpleef", 0, 0x700D00, "bow", MenuCategory.ARCADE,
        description = "Огненные баталии за первенство. Докажи другим, что ты — мастер ловкости! Внимательно " +
                "следи за игрой и выстреливай соперников стрелами."
    ),
    GameMode("makers", "MakersParty", 0, 0x133778, "makers", MenuCategory.ARCADE),
    GameMode("hub", "Хаб #2", 36, 0x00442C, "hub", MenuCategory.HUBS),
    GameMode("hub3", "Хаб #3", 32, 0x00442C, "hub", MenuCategory.HUBS)
) + listOf(
    "Симулятор\nШапок", "Duels - Лобби", "CactusFactory #112", "SCP лобби #300", "Вино друзей", "Evolve Lobby",
    "Duels - FFA", "Azerus #322", "TEST-66", "Duels - Арена", "Cristalix", "CivCraft Lobby #12",
    "CastleFight 2x2 #76", "Cristalix Personalization", "Steal An Egg", "CactusFactory #113", "JediCraft NewGen #446",
    "DontLetThemIn Lobby #536", "Зоопарк", "Лобби Аркад #22", "One-Punch Man #4351", "Cristalix p13n",
    "Настольные игры [ОБТ]", "WADA #123"
).mapIndexed { index, name ->
    GameMode("test-$index", name, if (index == 0) 4 else if (index < 5) 1 else 0, 0x0C2442, "lobby", MenuCategory.TESTING)
}

internal class MenuModel(private val favoritesFile: Path? = null) {

    val favorites = linkedSetOf<String>()
    var query = ""
    var category = MenuCategory.ALL
    var language = "RU"
    var english: Boolean
        get() = language == "US"
        set(value) {
            language = if (value) "US" else "RU"
        }
    var favoritesOnly = false
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
            MenuCategory.ALL -> game.category != MenuCategory.TESTING && game.category != MenuCategory.HUBS
            MenuCategory.FAVORITES -> game.id in favorites
            else -> game.category == category
        }
        val inLanguage = language == "RU" || game.id !in setOf("prison", "gta", "bingo", "oldschool", "io", "computer")
        (inCategory && inLanguage && (!favoritesOnly || game.id in favorites)) &&
                game.name.replace('\n', ' ').contains(query.trim(), ignoreCase = true)
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

    @Suppress("UnusedParameter")
    fun label(russian: String, english: String) = russian
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
