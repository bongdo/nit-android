package space.bongdo.nit.ui

data class SlashCommand(val id: String, val description: String)

val SLASH_COMMANDS = listOf(
    SlashCommand("new", "Новый чат в этой папке"),
    SlashCommand("dir", "Сменить папку по умолчанию"),
    SlashCommand("settings", "Модель, агент, тема"),
    SlashCommand("compact", "Сжать контекст сессии"),
    SlashCommand("revert", "Откатить последнее сообщение и вернуть в черновик"),
    SlashCommand("fork", "Форкнуть перед последним сообщением"),
    SlashCommand("fork-here", "Форкнуть в конце, сохранив всё"),
    SlashCommand("retry", "Повторить последний промпт"),
    SlashCommand("theme", "Выбрать тему"),
    SlashCommand("clear", "Очистить ввод"),
    SlashCommand("help", "Показать команды"),
)
