package space.bongdo.nit.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ModelRef(
    val id: String = "",
    val providerID: String = "",
    val variant: String? = null,
)

@Serializable
data class Tokens(
    val input: Long? = null,
    val output: Long? = null,
    val reasoning: Long? = null,
    val cache: CacheTokens? = null,
)

@Serializable
data class CacheTokens(val read: Long? = null, val write: Long? = null)

@Serializable
data class UserFile(
    val uri: String? = null,
    val name: String? = null,
    val mime: String? = null,
    val bytes: Long? = null,
    val description: String? = null,
)

@Serializable
data class SessionTime(
    val created: Long = 0,
    val updated: Long = 0,
    val busy: Long? = null,
    val idle: Long? = null,
    val viewed: Long? = null,
    val archived: Long? = null,
)

@Serializable
data class SessionLocation(val directory: String = "")

@Serializable
data class Session(
    val id: String,
    val parentID: String? = null,
    val title: String? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
    val cost: Double = 0.0,
    val tokens: Tokens? = null,
    val outcome: String? = null,
    val time: SessionTime = SessionTime(),
    val location: SessionLocation = SessionLocation(),
    val instance: String? = null,
)

@Serializable
data class SessionList(
    val data: List<Session> = emptyList(),
    val cursor: Cursor? = null,
)

@Serializable
data class Cursor(val next: String? = null, val previous: String? = null)

@Serializable
data class MessageTime(
    val created: Long = 0,
    val streamed: Long? = null,
    val completed: Long? = null,
)

@Serializable
data class MessageError(
    val name: String? = null,
    val message: String? = null,
    val type: String? = null,
    val status: Int? = null,
    val retryAfterMs: Long? = null,
)

@Serializable
data class ToolError(val name: String? = null, val message: String? = null)

@Serializable
data class ToolContent(
    val type: String = "",
    val text: String? = null,
    val uri: String? = null,
    val url: String? = null,
    val mime: String? = null,
    val name: String? = null,
)

@Serializable
data class ToolState(
    val status: String = "streaming",
    val input: JsonElement? = null,
    val content: List<ToolContent>? = null,
    val metadata: JsonElement? = null,
    val error: ToolError? = null,
)

@Serializable
data class ToolPart(
    val type: String = "tool",
    val id: String = "",
    val name: String = "",
    val executed: Boolean? = null,
    val state: ToolState = ToolState(),
    val time: PartTime? = null,
    val light: Boolean? = null,
)

@Serializable
data class PartTime(val created: Long? = null, val ran: Long? = null, val completed: Long? = null)

@Serializable
data class Part(
    val type: String = "",
    val text: String = "",
    val ordinal: Int? = null,
    val id: String = "",
    val name: String = "",
    val executed: Boolean? = null,
    val state: ToolState? = null,
    val time: PartTime? = null,
    val light: Boolean? = null,
)

@Serializable
data class EditedFile(
    val path: String = "",
    val additions: Int? = null,
    val deletions: Int? = null,
)

@Serializable
data class ShellOutput(val output: String = "", val truncated: Boolean? = null)

@Serializable
data class Message(
    val id: String,
    val type: String = "",
    val time: MessageTime = MessageTime(),
    val text: String? = null,
    val delivery: String? = null,
    val files: List<UserFile>? = null,
    val content: List<Part>? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
    val cost: Double? = null,
    val tokens: Tokens? = null,
    val outcome: String? = null,
    val error: MessageError? = null,
    val finish: String? = null,
    val command: String? = null,
    val status: String? = null,
    val output: ShellOutput? = null,
    val exit: JsonElement? = null,
    val steps: Int? = null,
    val tools: Int? = null,
    val errors: Int? = null,
    val edited: List<EditedFile>? = null,
    val name: String? = null,
    val description: String? = null,
    val summary: String? = null,
    val reason: String? = null,
)

@Serializable
data class MessagesPage(
    val data: List<Message> = emptyList(),
    val previous: String? = null,
)

@Serializable
data class Project(
    val id: String = "",
    val canonical: String = "",
    val vcs: String? = null,
)

@Serializable
data class FolderListing(
    val path: String = "",
    val parent: String = "",
    val dirs: List<String> = emptyList(),
    val truncated: Boolean? = null,
)

@Serializable
data class PermissionRequest(
    val id: String,
    val sessionID: String = "",
    val action: String = "",
    val resources: List<String> = emptyList(),
    val save: List<String>? = null,
    val metadata: JsonElement? = null,
    val message: String? = null,
)

@Serializable
data class FormOption(val value: String = "", val label: String = "", val description: String? = null)

@Serializable
data class FormCondition(
    val key: String = "",
    val op: String = "eq",
    val value: JsonElement? = null,
)

@Serializable
data class FormField(
    val key: String = "",
    val type: String = "",
    val title: String? = null,
    val description: String? = null,
    val placeholder: String? = null,
    val url: String? = null,
    val required: Boolean? = null,
    val hidden: Boolean? = null,
    val custom: Boolean? = null,
    val default: JsonElement? = null,
    val options: List<FormOption>? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    @SerialName("when") val conditions: List<FormCondition>? = null,
)

@Serializable
data class FormInfo(
    val id: String,
    val sessionID: String = "",
    val title: String = "",
    val metadata: JsonElement? = null,
    val fields: List<FormField> = emptyList(),
)

@Serializable
data class Agent(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val mode: String? = null,
)

@Serializable
data class Model(
    val id: String = "",
    val modelID: String = "",
    val providerID: String = "",
    val name: String = "",
    val status: String = "",
    val enabled: Boolean = true,
    val variants: List<Variant> = emptyList(),
    val limit: ModelLimit? = null,
)

@Serializable
data class ModelLimit(val context: Long = 0, val output: Long = 0)

@Serializable
data class Variant(val id: String = "")

@Serializable
data class UploadedFile(val path: String = "", val name: String = "", val size: Long = 0)

@Serializable
data class DraftResponse(
    val text: String = "",
    val cleared: Boolean = false,
    val updatedAt: Long = 0,
)

@Serializable
data class RemoteUiState(
    val hidden: List<String> = emptyList(),
    val status: Map<String, String> = emptyMap(),
    val showHidden: Boolean = false,
    val model: ModelRef? = null,
    val agent: String? = null,
)

@Serializable
data class StreamEvent(
    val id: String? = null,
    val created: Long? = null,
    val type: String = "",
    val data: JsonElement? = null,
)

@Serializable
data class ApiErrorBody(val error: String? = null, val message: String? = null)

class ApiException(message: String, val status: Int = 0) : Exception(message)

class UnauthorizedException(message: String = "session expired") : Exception(message)
