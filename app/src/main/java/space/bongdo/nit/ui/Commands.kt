package space.bongdo.nit.ui

data class SlashCommand(val id: String, val description: String)

val SLASH_COMMANDS = listOf(
    SlashCommand("new", "New chat in this folder"),
    SlashCommand("dir", "Change the default folder"),
    SlashCommand("settings", "Model, agent, theme"),
    SlashCommand("compact", "Compact the session context"),
    SlashCommand("revert", "Revert the last message and restore the draft"),
    SlashCommand("fork", "Fork before the last message"),
    SlashCommand("fork-here", "Fork at the end, keeping everything"),
    SlashCommand("retry", "Retry the last prompt"),
    SlashCommand("theme", "Choose theme"),
    SlashCommand("clear", "Clear input"),
    SlashCommand("help", "Show commands"),
)
